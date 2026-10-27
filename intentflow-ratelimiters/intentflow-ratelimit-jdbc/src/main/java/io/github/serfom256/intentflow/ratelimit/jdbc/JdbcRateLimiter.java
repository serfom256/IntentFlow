package io.github.serfom256.intentflow.ratelimit.jdbc;

import io.github.serfom256.intentflow.core.api.IntentContext;
import io.github.serfom256.intentflow.core.pipeline.LlmRateLimiter;
import io.github.serfom256.intentflow.core.pipeline.RateLimitExceededException;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.regex.Pattern;

@Slf4j
public final class JdbcRateLimiter implements LlmRateLimiter {

    public static final String CHANGELOG = "db/intentflow/intentflow-ratelimit.changelog.yaml";

    private static final int ATTEMPTS = 20;
    private static final Pattern TABLE = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?");

    private final DataSource dataSource;
    private final String table;
    private final int questions;
    private final Duration period;
    private final Function<IntentContext, String> key;
    private final boolean failOpen;
    private final Clock clock;
    private final AtomicLong cleanedAt = new AtomicLong();

    public JdbcRateLimiter(DataSource dataSource, String table, boolean createTable, int questions, Duration period,
            Function<IntentContext, String> key, boolean failOpen, Clock clock) {
        if (!TABLE.matcher(table).matches()) {
            throw new IllegalArgumentException("not a table name: " + table);
        }
        if (questions < 1) {
            throw new IllegalArgumentException("questions must be positive: " + questions);
        }
        if (period.toMillis() < 1) {
            throw new IllegalArgumentException("period must be at least 1ms: " + period);
        }
        this.dataSource = dataSource;
        this.table = table;
        this.questions = questions;
        this.period = period;
        this.key = key;
        this.failOpen = failOpen;
        this.clock = clock;
        if (createTable) {
            createIfMissing();
        }
    }

    @Override
    public void acquire(IntentContext caller) {
        String bucket = hash(key.apply(caller));
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.getAutoCommit()) {
                connection.setAutoCommit(true);
            }
            long now = clock.millis();
            cleanUp(connection, now);
            for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
                if (take(connection, bucket, now)) {
                    return;
                }
            }
            throw new RateLimitExceededException("The rate limit store is too busy; try again", Duration.ofSeconds(1));
        } catch (RateLimitExceededException e) {
            throw e;
        } catch (SQLException | RuntimeException e) {
            if (!failOpen) {
                throw new RateLimitExceededException("The rate limit store is unavailable", Duration.ofSeconds(5));
            }
            log.warn("JDBC rate limit check failed, letting the question through: {}", e.toString());
        }
    }

    private boolean take(Connection connection, String bucket, long now) throws SQLException {
        double tokens;
        long refilledAt;
        long revision;
        try (PreparedStatement select = connection
                .prepareStatement("SELECT tokens, refilled_at, revision FROM " + table + " WHERE bucket = ?")) {
            select.setString(1, bucket);
            try (ResultSet row = select.executeQuery()) {
                if (!row.next()) {
                    return insert(connection, bucket, now);
                }
                tokens = row.getDouble(1);
                refilledAt = row.getLong(2);
                revision = row.getLong(3);
            }
        }
        double perMilli = (double) questions / period.toMillis();
        double available = Math.min(questions, tokens + Math.max(0, now - refilledAt) * perMilli);
        if (available < 1) {
            long waitMillis = (long) Math.ceil((1 - available) / perMilli);
            throw new RateLimitExceededException("Rate limit of " + questions + " questions per " + period + " reached",
                    Duration.ofMillis(waitMillis));
        }
        try (PreparedStatement update = connection.prepareStatement("UPDATE " + table
                + " SET tokens = ?, refilled_at = ?, revision = ? WHERE bucket = ? AND revision = ?")) {
            update.setDouble(1, available - 1);
            update.setLong(2, now);
            update.setLong(3, revision + 1);
            update.setString(4, bucket);
            update.setLong(5, revision);
            return update.executeUpdate() == 1;
        }
    }

    private boolean insert(Connection connection, String bucket, long now) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO " + table + " (bucket, tokens, refilled_at, revision) VALUES (?, ?, ?, 0)")) {
            insert.setString(1, bucket);
            insert.setDouble(2, questions - 1);
            insert.setLong(3, now);
            insert.executeUpdate();
            return true;
        } catch (SQLException e) {
            if (e.getSQLState() != null && e.getSQLState().startsWith("23")) {
                return false;
            }
            throw e;
        }
    }

    private void cleanUp(Connection connection, long now) throws SQLException {
        long last = cleanedAt.get();
        if (now - last < period.toMillis() || !cleanedAt.compareAndSet(last, now)) {
            return;
        }
        try (PreparedStatement delete = connection
                .prepareStatement("DELETE FROM " + table + " WHERE refilled_at < ?")) {
            delete.setLong(1, now - period.toMillis());
            delete.executeUpdate();
        }
    }

    private void createIfMissing() {
        try (Connection connection = dataSource.getConnection()) {
            if (exists(connection.getMetaData())) {
                return;
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE " + table + " (bucket VARCHAR(64) NOT NULL PRIMARY KEY, "
                        + "tokens DOUBLE PRECISION NOT NULL, refilled_at NUMERIC(19) NOT NULL, "
                        + "revision NUMERIC(19) NOT NULL)");
            } catch (SQLException raced) {
                if (!exists(connection.getMetaData())) {
                    throw raced;
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create the rate limit table " + table + "; create it "
                    + "yourself, for example with " + CHANGELOG + " in your Liquibase changelog", e);
        }
    }

    private boolean exists(DatabaseMetaData metaData) throws SQLException {
        int dot = table.indexOf('.');
        String schema = dot < 0 ? null : table.substring(0, dot);
        String name = dot < 0 ? table : table.substring(dot + 1);
        for (String candidate : List.of(name, name.toLowerCase(Locale.ROOT), name.toUpperCase(Locale.ROOT))) {
            for (String inSchema : schema == null ? Collections.<String>singletonList(null)
                    : List.of(schema, schema.toLowerCase(Locale.ROOT), schema.toUpperCase(Locale.ROOT))) {
                try (ResultSet tables = metaData.getTables(null, inSchema, candidate, null)) {
                    if (tables.next()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static String hash(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
