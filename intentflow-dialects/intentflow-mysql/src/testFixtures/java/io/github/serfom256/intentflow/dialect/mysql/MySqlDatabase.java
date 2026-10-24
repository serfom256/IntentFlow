package io.github.serfom256.intentflow.dialect.mysql;

import io.github.serfom256.intentflow.core.pipeline.SqlDialect;
import io.github.serfom256.intentflow.core.testing.ComposeDatabases;
import io.github.serfom256.intentflow.core.testing.TestDatabase;
import org.testcontainers.mysql.MySQLContainer;

public enum MySqlDatabase implements TestDatabase {

    MYSQL;

    private Endpoint endpoint;

    @Override
    public synchronized Endpoint endpoint() {
        if (endpoint == null) {
            if (ComposeDatabases.enabled()) {
                endpoint = new Endpoint("jdbc:mysql://localhost:" + ComposeDatabases.port("MYSQL_PORT", 13306)
                        + "/intentflow", "intentflow", "intentflow");
            } else {
                MySQLContainer container = new MySQLContainer("mysql:8.4");
                container.start();
                endpoint = new Endpoint(container.getJdbcUrl(), container.getUsername(), container.getPassword());
            }
        }
        return endpoint;
    }

    @Override
    public SqlDialect dialect() {
        return new MySqlDialect();
    }

    @Override
    public String createGadgetTable() {
        return """
                CREATE TABLE gadget (
                    id BIGINT PRIMARY KEY,
                    name VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin,
                    kind VARCHAR(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin,
                    price DECIMAL(10,2),
                    stock INT,
                    active BOOLEAN,
                    created_at DATETIME)
                """;
    }
}
