package io.github.serfom256.intentflow.core.testing;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class RelationTables {

    private RelationTables() {
    }

    public static TestDatabase.Endpoint prepare(TestDatabase database) {
        TestDatabase.Endpoint endpoint = database.endpoint();
        try (Connection c = DriverManager.getConnection(endpoint.url(), endpoint.user(), endpoint.password());
                Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS rel_order");
            s.execute("DROP TABLE IF EXISTS rel_customer");
            s.execute("CREATE TABLE rel_customer (id BIGINT PRIMARY KEY, name VARCHAR(40), country VARCHAR(2), "
                    + "vip BOOLEAN)");
            s.execute("CREATE TABLE rel_order (id BIGINT PRIMARY KEY, customer_id BIGINT, status VARCHAR(20), "
                    + "total DECIMAL(10,2))");
            s.execute("INSERT INTO rel_customer VALUES (1, 'Ann', 'DE', TRUE), (2, 'Bob', 'DE', FALSE), "
                    + "(3, 'Cid', 'FR', FALSE), (4, 'Dan', 'FR', TRUE)");
            s.execute("INSERT INTO rel_order VALUES (10, 1, 'NEW', 150.00), (11, 1, 'PAID', 50.00), "
                    + "(12, 2, 'PAID', 150.00), (13, 3, 'CANCELLED', 20.00), (14, 3, 'PAID', 300.00)");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create relation tables", e);
        }
        return endpoint;
    }
}
