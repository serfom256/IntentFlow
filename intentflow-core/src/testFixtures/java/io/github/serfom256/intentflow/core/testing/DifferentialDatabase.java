package io.github.serfom256.intentflow.core.testing;

import io.github.serfom256.intentflow.core.pipeline.SqlDialect;
import io.github.serfom256.intentflow.dialect.mysql.MySqlDialect;
import io.github.serfom256.intentflow.dialect.postgresql.PostgresDialect;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.function.Supplier;

public enum DifferentialDatabase implements TestDatabase {

    POSTGRES(() -> new PostgreSQLContainer("postgres:17.4"), PostgresDialect::new,
            "jdbc:postgresql://localhost:" + ComposeDatabases.port("POSTGRES_PORT", 15432) + "/intentflow", """
                    CREATE TABLE gadget (
                        id BIGINT PRIMARY KEY,
                        name TEXT COLLATE "C",
                        kind TEXT COLLATE "C",
                        price NUMERIC(10,2),
                        stock INTEGER,
                        active BOOLEAN,
                        created_at TIMESTAMP)
                    """),

    MYSQL(() -> new MySQLContainer("mysql:8.4"), MySqlDialect::new,
            "jdbc:mysql://localhost:" + ComposeDatabases.port("MYSQL_PORT", 13306) + "/intentflow", """
                    CREATE TABLE gadget (
                        id BIGINT PRIMARY KEY,
                        name VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin,
                        kind VARCHAR(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin,
                        price DECIMAL(10,2),
                        stock INT,
                        active BOOLEAN,
                        created_at DATETIME)
                    """);

    private final Supplier<JdbcDatabaseContainer<?>> containerFactory;
    private final Supplier<SqlDialect> dialect;
    private final String composeUrl;
    private final String createGadgetTable;
    private Endpoint endpoint;

    DifferentialDatabase(Supplier<JdbcDatabaseContainer<?>> containerFactory, Supplier<SqlDialect> dialect,
            String composeUrl, String createGadgetTable) {
        this.containerFactory = containerFactory;
        this.dialect = dialect;
        this.composeUrl = composeUrl;
        this.createGadgetTable = createGadgetTable;
    }

    @Override
    public synchronized Endpoint endpoint() {
        if (endpoint == null) {
            if (ComposeDatabases.enabled()) {
                endpoint = new Endpoint(composeUrl, "intentflow", "intentflow");
            } else {
                JdbcDatabaseContainer<?> c = containerFactory.get();
                c.start();
                endpoint = new Endpoint(c.getJdbcUrl(), c.getUsername(), c.getPassword());
            }
        }
        return endpoint;
    }

    @Override
    public SqlDialect dialect() {
        return dialect.get();
    }

    @Override
    public String createGadgetTable() {
        return createGadgetTable;
    }
}
