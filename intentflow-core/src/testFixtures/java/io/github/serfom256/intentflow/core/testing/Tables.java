package io.github.serfom256.intentflow.core.testing;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Collections;
import java.util.List;

public final class Tables {

    private Tables() {
    }

    public static void drop(Statement statement, String table) {
        try {
            statement.execute("DROP TABLE " + table);
        } catch (SQLException missing) {
            return;
        }
    }

    public static void insert(Connection connection, String table, List<List<Object>> rows) throws SQLException {
        String values = String.join(", ", Collections.nCopies(rows.getFirst().size(), "?"));
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO " + table + " VALUES (" + values + ")")) {
            for (List<Object> row : rows) {
                for (int i = 0; i < row.size(); i++) {
                    Object value = row.get(i);
                    if (value == null) {
                        insert.setNull(i + 1, Types.VARCHAR);
                    } else if (value instanceof Boolean flag) {
                        insert.setBoolean(i + 1, flag);
                    } else {
                        insert.setObject(i + 1, value);
                    }
                }
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }
}
