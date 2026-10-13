package io.github.serfom256.intentflow.springdata.jdbc;

import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.core.convert.EntityRowMapper;
import org.springframework.data.jdbc.core.convert.JdbcConverter;
import org.springframework.data.jdbc.core.convert.MappingJdbcConverter;
import org.springframework.data.jdbc.core.mapping.JdbcMappingContext;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

public final class SpringDataJdbcQueryExecutor implements QueryExecutor {

    private final JdbcClient jdbcClient;
    private final JdbcConverter converter;

    public SpringDataJdbcQueryExecutor(JdbcClient jdbcClient, JdbcConverter converter) {
        this.jdbcClient = jdbcClient;
        this.converter = converter;
    }

    public static SpringDataJdbcQueryExecutor of(DataSource dataSource) {
        JdbcMappingContext context = new JdbcMappingContext();
        MappingJdbcConverter converter = new MappingJdbcConverter(context, (identifier, path) -> {
            throw new UnsupportedOperationException("IntentFlow reads single-table entities; "
                    + path.toDotPath() + " needs a relation");
        });
        return new SpringDataJdbcQueryExecutor(JdbcClient.create(dataSource), converter);
    }

    @Override
    public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
        @SuppressWarnings("unchecked")
        RelationalPersistentEntity<T> entity =
                (RelationalPersistentEntity<T>) converter.getMappingContext().getRequiredPersistentEntity(resultType);
        return jdbcClient.sql(query.sql())
                .params(query.values())
                .query(new EntityRowMapper<>(entity, converter))
                .list();
    }

    @Override
    public List<List<@Nullable Object>> rows(BoundQuery query) {
        return jdbcClient.sql(query.sql()).params(query.values()).query((rs, n) -> {
            int columns = rs.getMetaData().getColumnCount();
            List<@Nullable Object> row = new ArrayList<>(columns);
            for (int i = 1; i <= columns; i++) {
                row.add(rs.getObject(i));
            }
            return row;
        }).list();
    }
}
