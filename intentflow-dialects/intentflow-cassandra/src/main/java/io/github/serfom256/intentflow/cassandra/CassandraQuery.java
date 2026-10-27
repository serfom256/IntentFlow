package io.github.serfom256.intentflow.cassandra;

import com.datastax.oss.driver.api.core.cql.Row;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.DslLiteral;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public record CassandraQuery(List<String> segments, List<DslLiteral> parameters, List<Object> bindValues,
        Predicate<Row> filter, @Nullable Comparator<Row> order, int skip, int limit,
        @Nullable CassandraAggregate aggregate, @Nullable UnaryOperator<List<Row>> top) implements BoundQuery {

    public CassandraQuery {
        segments = List.copyOf(segments);
        parameters = List.copyOf(parameters);
        bindValues = List.copyOf(bindValues);
    }

    public CassandraQuery(List<String> segments, List<DslLiteral> parameters, List<Object> bindValues,
            Predicate<Row> filter, @Nullable Comparator<Row> order, int skip, int limit) {
        this(segments, parameters, bindValues, filter, order, skip, limit, null, null);
    }

    public String cql() {
        return sql();
    }
}
