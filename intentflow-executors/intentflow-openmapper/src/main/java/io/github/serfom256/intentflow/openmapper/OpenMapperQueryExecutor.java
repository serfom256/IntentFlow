package io.github.serfom256.intentflow.openmapper;

import io.github.serfom256.annotations.entity.Model;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import io.github.serfom256.intentflow.core.pipeline.RelatedRows;
import io.github.serfom256.procedures.Procedures;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class OpenMapperQueryExecutor implements QueryExecutor {

    private final Procedures procedures;
    private final @Nullable JdbcClient jdbcClient;

    public OpenMapperQueryExecutor(Procedures procedures) {
        this(procedures, null);
    }

    public OpenMapperQueryExecutor(Procedures procedures, @Nullable JdbcClient jdbcClient) {
        this.procedures = procedures;
        this.jdbcClient = jdbcClient;
    }

    @Override
    public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
        return execute(query, resultType, RelatedRows.NONE);
    }

    @Override
    public <T> List<T> execute(BoundQuery query, Class<T> resultType, RelatedRows related) {
        List<T> items = select(query, resultType);
        new JoinedLoader(related, this::select).load(resultType, items);
        return items;
    }

    private <T> List<T> select(BoundQuery query, Class<T> resultType) {
        if (resultType.getAnnotation(Model.class) == null) {
            throw new IllegalArgumentException(resultType.getName() + " is not an OpenMapper @Model");
        }
        List<Object> values = query.values();
        Map<String, Object> parameters = new HashMap<>(values.size() * 2);
        for (int i = 0; i < values.size(); i++) {
            parameters.put("p" + (i + 1), values.get(i));
        }
        return procedures.select(query.render(i -> "[p" + i + "]"), resultType, parameters);
    }

    @Override
    public List<List<@Nullable Object>> rows(BoundQuery query) {
        if (jdbcClient == null) {
            throw new UnsupportedOperationException(
                    "OpenMapper maps rows to @Model classes only; " + "pass a JdbcClient to run aggregate queries");
        }
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
