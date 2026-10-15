package io.github.serfom256.intentflow.jimmer;

import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import org.babyfish.jimmer.meta.ImmutableProp;
import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.meta.SingleColumn;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;
import org.jspecify.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public final class JimmerQueryExecutor implements QueryExecutor {

    private final JSqlClientImplementor sqlClient;

    public JimmerQueryExecutor(JSqlClient sqlClient) {
        this.sqlClient = (JSqlClientImplementor) sqlClient;
    }

    @Override
    public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
        ImmutableType type = ImmutableType.get(resultType);
        if (!type.isEntity()) {
            throw new IllegalArgumentException(resultType.getName() + " is not a Jimmer @Entity");
        }
        ImmutableProp idProp = type.getIdProp();
        String idColumn = ((SingleColumn) idProp.getStorage(sqlClient.getMetadataStrategy())).getName();
        Class<?> idClass = boxed(idProp.getReturnClass());

        List<Object> ids = withConnection(connection -> {
            List<Object> out = new ArrayList<>();
            try (PreparedStatement statement = prepare(connection, query); ResultSet rs = statement.executeQuery()) {
                int column = rs.findColumn(idColumn);
                while (rs.next()) {
                    out.add(rs.getObject(column, idClass));
                }
            }
            return out;
        });
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Object, T> byId = sqlClient.getEntities().findMapByIds(resultType, ids);
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    @Override
    public List<List<@Nullable Object>> rows(BoundQuery query) {
        return withConnection(connection -> {
            List<List<@Nullable Object>> rows = new ArrayList<>();
            try (PreparedStatement statement = prepare(connection, query); ResultSet rs = statement.executeQuery()) {
                int columns = rs.getMetaData().getColumnCount();
                while (rs.next()) {
                    List<@Nullable Object> row = new ArrayList<>(columns);
                    for (int i = 1; i <= columns; i++) {
                        row.add(rs.getObject(i));
                    }
                    rows.add(row);
                }
            }
            return rows;
        });
    }

    private static PreparedStatement prepare(Connection connection, BoundQuery query) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(query.sql());
        List<@Nullable Object> values = query.values();
        for (int i = 0; i < values.size(); i++) {
            statement.setObject(i + 1, values.get(i));
        }
        return statement;
    }

    private <R> R withConnection(SqlFunction<R> work) {
        Function<Connection, R> function = connection -> {
            try {
                return work.apply(connection);
            } catch (SQLException e) {
                throw new IllegalStateException("IntentFlow query failed: " + e.getMessage(), e);
            }
        };
        return sqlClient.getConnectionManager().execute(function);
    }

    private static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == long.class) return Long.class;
        if (type == int.class) return Integer.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == boolean.class) return Boolean.class;
        if (type == char.class) return Character.class;
        return type;
    }

    @FunctionalInterface
    private interface SqlFunction<R> {
        R apply(Connection connection) throws SQLException;
    }
}
