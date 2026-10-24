package io.github.serfom256.intentflow.core.testing;

import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.catalog.impl.AnnotatedServiceReader;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.RecordComponent;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class PlainJdbcExecutor implements QueryExecutor {

    private static final Map<TestDatabase.Endpoint, Connection> CONNECTIONS = new ConcurrentHashMap<>();

    private final TestDatabase.Endpoint endpoint;

    PlainJdbcExecutor(TestDatabase.Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
        if (!resultType.isRecord()) {
            throw new IllegalArgumentException(resultType + " is not a record");
        }
        RecordComponent[] components = resultType.getRecordComponents();
        Class<?>[] types = new Class<?>[components.length];
        for (int i = 0; i < components.length; i++) {
            types[i] = components[i].getType();
        }
        return run(query, rs -> {
            Object[] args = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                args[i] = read(rs, AnnotatedServiceReader.snakeCase(components[i].getName()), types[i]);
            }
            try {
                return resultType.getDeclaredConstructor(types).newInstance(args);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Could not create " + resultType.getName(), e);
            }
        });
    }

    @Override
    public List<List<@Nullable Object>> rows(BoundQuery query) {
        return run(query, rs -> {
            List<@Nullable Object> row = new ArrayList<>();
            for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                row.add(rs.getObject(i));
            }
            return row;
        });
    }

    private interface RowReader<T> {
        T read(ResultSet rs) throws SQLException;
    }

    private <T> List<T> run(BoundQuery query, RowReader<T> reader) {
        try (PreparedStatement statement = connection().prepareStatement(query.sql())) {
            List<@Nullable Object> values = query.values();
            for (int i = 0; i < values.size(); i++) {
                statement.setObject(i + 1, values.get(i));
            }
            List<T> out = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    out.add(reader.read(rs));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new IllegalStateException("Query failed: " + query.sql(), e);
        }
    }

    private synchronized Connection connection() throws SQLException {
        Connection connection = CONNECTIONS.get(endpoint);
        if (connection == null || !connection.isValid(2)) {
            connection = DriverManager.getConnection(endpoint.url(), endpoint.user(), endpoint.password());
            CONNECTIONS.put(endpoint, connection);
        }
        return connection;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static @Nullable Object read(ResultSet rs, String column, Class<?> type) throws SQLException {
        if (type.isEnum()) {
            String name = rs.getString(column);
            return name == null ? null : Enum.valueOf((Class) type, name);
        }
        Class<?> boxed = type == long.class ? Long.class
                : type == int.class ? Integer.class
                        : type == boolean.class ? Boolean.class : type;
        return rs.getObject(column, boxed);
    }
}
