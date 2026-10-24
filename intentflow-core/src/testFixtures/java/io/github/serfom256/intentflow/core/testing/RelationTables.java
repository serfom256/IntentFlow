package io.github.serfom256.intentflow.core.testing;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public final class RelationTables {

    private RelationTables() {
    }

    public static TestDatabase.Endpoint prepare(TestDatabase database) {
        TestDatabase.Endpoint endpoint = database.endpoint();
        try (Connection c = DriverManager.getConnection(endpoint.url(), endpoint.user(), endpoint.password());
                Statement s = c.createStatement()) {
            Tables.drop(s, "rel_order");
            Tables.drop(s, "rel_customer");
            s.execute(database
                    .ddl("CREATE TABLE rel_customer (id BIGINT PRIMARY KEY, name VARCHAR(40), country VARCHAR(2), "
                            + "vip BOOLEAN)"));
            s.execute(database
                    .ddl("CREATE TABLE rel_order (id BIGINT PRIMARY KEY, customer_id BIGINT, status VARCHAR(20), "
                            + "total DECIMAL(10,2))"));
            Tables.insert(c, "rel_customer", List.of(List.of(1, "Ann", "DE", true), List.of(2, "Bob", "DE", false),
                    List.of(3, "Cid", "FR", false), List.of(4, "Dan", "FR", true)));
            Tables.insert(c, "rel_order", List.of(List.of(10, 1, "NEW", new BigDecimal("150.00")),
                    List.of(11, 1, "PAID", new BigDecimal("50.00")), List.of(12, 2, "PAID", new BigDecimal("150.00")),
                    List.of(13, 3, "CANCELLED", new BigDecimal("20.00")),
                    List.of(14, 3, "PAID", new BigDecimal("300.00"))));
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create relation tables", e);
        }
        return endpoint;
    }
}
