package io.github.serfom256.intentflow.ratelimit.jdbc;

import io.github.serfom256.intentflow.core.api.IntentContext;
import io.github.serfom256.intentflow.core.api.factories.IntentContexts;
import io.github.serfom256.intentflow.core.pipeline.RateLimitExceededException;
import io.github.serfom256.intentflow.core.testing.DriverManagerDataSource;
import io.github.serfom256.intentflow.core.testing.Tables;
import io.github.serfom256.intentflow.core.testing.TestDatabase;
import io.github.serfom256.intentflow.dialect.mysql.MySqlDatabase;
import io.github.serfom256.intentflow.dialect.oracle.OracleDatabase;
import io.github.serfom256.intentflow.dialect.postgresql.PostgresDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcRateLimiterTest {

    private static final IntentContext ANN = IntentContexts.of("ann", "acme", Set.of());
    private static final IntentContext BOB = IntentContexts.of("bob", "acme", Set.of());

    private final MovableClock clock = new MovableClock();

    private static final class MovableClock extends Clock {

        private volatile Instant now = Instant.parse("2026-10-24T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    static List<String> databases() {
        return List.of("H2", "POSTGRES", "MYSQL", "ORACLE");
    }

    private static DataSource dataSource(String database) {
        TestDatabase.Endpoint endpoint = switch (database) {
        case "H2" -> new TestDatabase.Endpoint("jdbc:h2:mem:ratelimit;DB_CLOSE_DELAY=-1", "sa", "");
        case "POSTGRES" -> PostgresDatabase.POSTGRES.endpoint();
        case "MYSQL" -> MySqlDatabase.MYSQL.endpoint();
        default -> OracleDatabase.FREE.endpoint();
        };
        DataSource dataSource = new DriverManagerDataSource(endpoint);
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            Tables.drop(s, "intentflow_rate_limit");
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return dataSource;
    }

    private JdbcRateLimiter limiter(DataSource dataSource, int questions) {
        return new JdbcRateLimiter(dataSource, "intentflow_rate_limit", true, questions, Duration.ofMinutes(1),
                caller -> caller.principal().orElse("anonymous"), true, clock);
    }

    @ParameterizedTest
    @MethodSource("databases")
    void instancesShareOneLimitPerCaller(String database) {
        DataSource dataSource = dataSource(database);
        JdbcRateLimiter first = limiter(dataSource, 3);
        JdbcRateLimiter second = limiter(dataSource, 3);

        first.acquire(ANN);
        second.acquire(ANN);
        first.acquire(ANN);

        assertThatThrownBy(() -> second.acquire(ANN)).isInstanceOfSatisfying(RateLimitExceededException.class,
                e -> assertThat(e.retryAfter()).isEqualTo(Duration.ofSeconds(20)));
        second.acquire(BOB);
        clock.advance(Duration.ofSeconds(20));
        second.acquire(ANN);
    }

    @ParameterizedTest
    @MethodSource("databases")
    void concurrentQuestionsNeverExceedTheLimit(String database) throws Exception {
        DataSource dataSource = dataSource(database);
        List<JdbcRateLimiter> instances = List.of(limiter(dataSource, 5), limiter(dataSource, 5));
        List<Callable<Boolean>> questions = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            JdbcRateLimiter limiter = instances.get(i % 2);
            questions.add(() -> {
                try {
                    limiter.acquire(ANN);
                    return true;
                } catch (RateLimitExceededException e) {
                    return false;
                }
            });
        }
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            long allowed = 0;
            for (Future<Boolean> answer : pool.invokeAll(questions)) {
                allowed += answer.get() ? 1 : 0;
            }
            assertThat(allowed).isEqualTo(5);
        } finally {
            pool.shutdownNow();
        }
    }

    @ParameterizedTest
    @MethodSource("databases")
    void bucketsAreHashedAndIdleOnesAreRemoved(String database) throws SQLException {
        DataSource dataSource = dataSource(database);
        JdbcRateLimiter limiter = limiter(dataSource, 3);
        limiter.acquire(ANN);

        assertThat(buckets(dataSource)).singleElement().asString().hasSize(64).doesNotContain("ann");
        clock.advance(Duration.ofMinutes(2));
        limiter.acquire(BOB);
        assertThat(buckets(dataSource)).hasSize(1);
    }

    @Test
    void anUnreachableStoreLetsQuestionsThroughUnlessFailClosed() {
        DataSource dataSource = dataSource("H2");
        JdbcRateLimiter open = limiter(dataSource, 1);
        JdbcRateLimiter closed = new JdbcRateLimiter(dataSource, "intentflow_rate_limit", false, 1,
                Duration.ofMinutes(1), caller -> caller.principal().orElse("anonymous"), false, clock);
        DataSource missing = new DriverManagerDataSource(
                new TestDatabase.Endpoint("jdbc:h2:mem:" + UUID.randomUUID() + ";IFEXISTS=TRUE", "sa", ""));
        JdbcRateLimiter unreachable = new JdbcRateLimiter(missing, "intentflow_rate_limit", false, 1,
                Duration.ofMinutes(1), caller -> caller.principal().orElse("anonymous"), false, clock);

        open.acquire(ANN);
        assertThatThrownBy(() -> closed.acquire(ANN)).isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("Rate limit of 1");
        assertThatThrownBy(() -> unreachable.acquire(ANN)).isInstanceOf(RateLimitExceededException.class)
                .hasMessage("The rate limit store is unavailable");
    }

    private static List<String> buckets(DataSource dataSource) throws SQLException {
        List<String> buckets = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
                Statement s = c.createStatement();
                ResultSet rows = s.executeQuery("SELECT bucket FROM intentflow_rate_limit")) {
            while (rows.next()) {
                buckets.add(rows.getString(1));
            }
        }
        return buckets;
    }
}
