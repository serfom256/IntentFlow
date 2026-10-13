package io.github.serfom256.intentflow.core.testing;

import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import io.github.serfom256.intentflow.core.pipeline.SqlBuilder;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Map;

public abstract class ExecutorDifferentialTest extends DifferentialTestBase {

    protected abstract QueryExecutor createExecutor(String jdbcUrl, String user, String password);

    protected DifferentialDatabase database() {
        return DifferentialDatabase.POSTGRES;
    }

    @Override
    protected SqlBuilder sqlBuilder() {
        return SqlBuilders.create(database().dialect());
    }

    @Override
    protected String target() {
        return database().name();
    }

    @Override
    protected QueryExecutor prepare(List<Map<String, Object>> rows) throws SQLException {
        DifferentialDatabase.Endpoint db = database().endpoint();
        try (Connection c = DriverManager.getConnection(db.url(), db.user(), db.password())) {
            try (Statement s = c.createStatement()) {
                s.execute("DROP TABLE IF EXISTS gadget");
                s.execute(database().createGadgetTable());
            }
            try (PreparedStatement insert = c.prepareStatement("INSERT INTO gadget VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                for (Map<String, Object> row : rows) {
                    insert.setLong(1, ((BigDecimal) row.get("id")).longValueExact());
                    insert.setObject(2, row.get("name"));
                    insert.setObject(3, row.get("kind"));
                    insert.setObject(4, row.get("price"));
                    insert.setObject(5, row.get("stock") == null ? null : ((BigDecimal) row.get("stock")).intValue(),
                            Types.INTEGER);
                    insert.setObject(6, row.get("active"), Types.BOOLEAN);
                    insert.setObject(7, row.get("created_at"), Types.TIMESTAMP);
                    insert.addBatch();
                }
                insert.executeBatch();
            }
        }
        return createExecutor(db.url(), db.user(), db.password());
    }
}
