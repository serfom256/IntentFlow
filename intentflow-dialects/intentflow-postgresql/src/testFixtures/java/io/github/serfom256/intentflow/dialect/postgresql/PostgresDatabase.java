package io.github.serfom256.intentflow.dialect.postgresql;

import io.github.serfom256.intentflow.core.pipeline.SqlDialect;
import io.github.serfom256.intentflow.core.testing.ComposeDatabases;
import io.github.serfom256.intentflow.core.testing.TestDatabase;
import org.testcontainers.postgresql.PostgreSQLContainer;

public enum PostgresDatabase implements TestDatabase {

    POSTGRES;

    private Endpoint endpoint;

    @Override
    public synchronized Endpoint endpoint() {
        if (endpoint == null) {
            if (ComposeDatabases.enabled()) {
                endpoint = new Endpoint(
                        "jdbc:postgresql://localhost:" + ComposeDatabases.port("POSTGRES_PORT", 15432) + "/intentflow",
                        "intentflow", "intentflow");
            } else {
                PostgreSQLContainer container = new PostgreSQLContainer("postgres:17.4");
                container.start();
                endpoint = new Endpoint(container.getJdbcUrl(), container.getUsername(), container.getPassword());
            }
        }
        return endpoint;
    }

    @Override
    public SqlDialect dialect() {
        return new PostgresDialect();
    }

    @Override
    public String createGadgetTable() {
        return """
                CREATE TABLE gadget (
                    id BIGINT PRIMARY KEY,
                    name TEXT COLLATE "C",
                    kind TEXT COLLATE "C",
                    price NUMERIC(10,2),
                    stock INTEGER,
                    active BOOLEAN,
                    created_at TIMESTAMP)
                """;
    }
}
