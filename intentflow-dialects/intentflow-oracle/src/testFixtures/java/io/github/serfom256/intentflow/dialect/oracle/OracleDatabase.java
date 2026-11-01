package io.github.serfom256.intentflow.dialect.oracle;

import io.github.serfom256.intentflow.core.pipeline.SqlDialect;
import io.github.serfom256.intentflow.core.testing.TestDatabase;
import org.testcontainers.oracle.OracleContainer;

import java.time.Duration;

public enum OracleDatabase implements TestDatabase {

    FREE {
        @Override
        Endpoint start() {
            OracleContainer container = new OracleContainer("gvenzl/oracle-free:23.26.3-slim-faststart")
                    .withStartupTimeout(Duration.ofMinutes(5));
            container.start();
            return new Endpoint(container.getJdbcUrl(), container.getUsername(), container.getPassword());
        }

        @Override
        public String ddl(String createTable) {
            return createTable.replace("BIGINT", "NUMBER(19)");
        }
    };

    private Endpoint endpoint;

    abstract Endpoint start();

    @Override
    public synchronized Endpoint endpoint() {
        if (endpoint == null) {
            endpoint = start();
        }
        return endpoint;
    }

    @Override
    public SqlDialect dialect() {
        return new OracleDialect();
    }

    @Override
    public String createGadgetTable() {
        return """
                CREATE TABLE gadget (
                    id NUMBER(19) PRIMARY KEY,
                    name VARCHAR2(32),
                    kind VARCHAR2(16),
                    price NUMBER(10,2),
                    stock NUMBER(10),
                    active NUMBER(1),
                    created_at TIMESTAMP)
                """;
    }
}
