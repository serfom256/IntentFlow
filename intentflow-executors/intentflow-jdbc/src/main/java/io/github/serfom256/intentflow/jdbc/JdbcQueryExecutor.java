package io.github.serfom256.intentflow.jdbc;

import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

public final class JdbcQueryExecutor implements QueryExecutor {

    private final JdbcClient jdbc;
    private final ClassValue<RowMapper<?>> mappers = new ClassValue<>() {
        @Override
        protected RowMapper<?> computeValue(Class<?> type) {
            return DataClassRowMapper.newInstance(type);
        }
    };

    public JdbcQueryExecutor(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public static JdbcQueryExecutor of(DataSource dataSource) {
        return new JdbcQueryExecutor(JdbcClient.create(dataSource));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
        RowMapper<T> mapper = (RowMapper<T>) mappers.get(resultType);
        return jdbc.sql(query.sql()).params(query.values()).query(mapper).list();
    }

    @Override
    public List<List<@Nullable Object>> rows(BoundQuery query) {
        return jdbc.sql(query.sql()).params(query.values()).query((rs, n) -> {
            int columns = rs.getMetaData().getColumnCount();
            List<@Nullable Object> row = new ArrayList<>(columns);
            for (int i = 1; i <= columns; i++) {
                row.add(rs.getObject(i));
            }
            return row;
        }).list();
    }
}
