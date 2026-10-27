package io.github.serfom256.intentflow.admin;

import io.github.serfom256.intentflow.core.catalog.CatalogOverrides;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.catalog.ServiceOverride;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceNames;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

public final class JdbcCatalogOverrides implements CatalogOverrides {

    public static final String CHANGELOG = "db/intentflow/intentflow-admin.changelog.yaml";

    private static final Pattern TABLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,62}");
    private static final String SERVICE_LEVEL = "";
    private static final String DESCRIPTION = "DESCRIPTION";
    private static final String URI = "URI";
    private static final String FIELD_DESCRIPTION = "FIELD_DESCRIPTION";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final String table;
    private final long refreshNanos;
    private final AtomicLong version = new AtomicLong();
    private volatile Map<ServiceName, ServiceOverride> snapshot = Map.of();
    private volatile long loadedAt;

    public JdbcCatalogOverrides(DataSource dataSource, String table, boolean createTable, Duration refreshInterval) {
        if (!TABLE_NAME.matcher(table).matches()) {
            throw new IllegalArgumentException("Invalid table name for catalog overrides: " + table);
        }
        this.jdbc = new JdbcTemplate(dataSource);
        this.transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        this.table = table;
        this.refreshNanos = refreshInterval.toNanos();
        if (createTable) {
            jdbc.execute("CREATE TABLE IF NOT EXISTS " + table + " (service VARCHAR(100) NOT NULL, "
                    + "field VARCHAR(100) NOT NULL, kind VARCHAR(20) NOT NULL, text_value VARCHAR(2000) NOT NULL, "
                    + "PRIMARY KEY (service, field, kind))");
        }
        try {
            reload();
        } catch (BadSqlGrammarException e) {
            throw new IllegalStateException("The IntentFlow admin table " + table + " cannot be read. Include "
                    + "classpath:" + CHANGELOG + " in your Liquibase changelog, or set "
                    + "intentflow.admin.create-table=true to let the admin create it.", e);
        }
    }

    @Override
    public Optional<ServiceOverride> find(ServiceName service) {
        return Optional.ofNullable(current().get(service));
    }

    @Override
    public Map<ServiceName, ServiceOverride> all() {
        return current();
    }

    @Override
    public void put(ServiceName service, ServiceOverride override) {
        transactions.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM " + table + " WHERE service = ?", service.value());
            String insert = "INSERT INTO " + table + " (service, field, kind, text_value) VALUES (?, ?, ?, ?)";
            if (override.description() != null) {
                jdbc.update(insert, service.value(), SERVICE_LEVEL, DESCRIPTION, override.description());
            }
            if (override.uri() != null) {
                jdbc.update(insert, service.value(), SERVICE_LEVEL, URI, override.uri());
            }
            override.fieldDescriptions()
                    .forEach((field, text) -> jdbc.update(insert, service.value(), field, FIELD_DESCRIPTION, text));
        });
        reload();
    }

    @Override
    public void remove(ServiceName service) {
        jdbc.update("DELETE FROM " + table + " WHERE service = ?", service.value());
        reload();
    }

    @Override
    public long version() {
        current();
        return version.get();
    }

    private Map<ServiceName, ServiceOverride> current() {
        if (System.nanoTime() - loadedAt >= refreshNanos) {
            reload();
        }
        return snapshot;
    }

    private synchronized void reload() {
        Map<String, String[]> services = new LinkedHashMap<>();
        Map<String, Map<String, String>> fields = new HashMap<>();
        jdbc.query("SELECT service, field, kind, text_value FROM " + table + " ORDER BY service, field", rs -> {
            String service = rs.getString(1);
            String[] values = services.computeIfAbsent(service, s -> new String[2]);
            switch (rs.getString(3)) {
            case DESCRIPTION -> values[0] = rs.getString(4);
            case URI -> values[1] = rs.getString(4);
            case FIELD_DESCRIPTION ->
                fields.computeIfAbsent(service, s -> new LinkedHashMap<>()).put(rs.getString(2), rs.getString(4));
            default -> {
            }
            }
        });
        Map<ServiceName, ServiceOverride> loaded = new LinkedHashMap<>();
        services.forEach((service, values) -> loaded.put(ServiceNames.of(service),
                new ServiceOverride(values[0], values[1], fields.getOrDefault(service, Map.of()))));
        if (!loaded.equals(snapshot)) {
            snapshot = Map.copyOf(loaded);
            version.incrementAndGet();
        }
        loadedAt = System.nanoTime();
    }
}
