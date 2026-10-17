package io.github.serfom256.intentflow.cassandra;

import com.datastax.oss.driver.api.core.cql.Row;
import io.github.serfom256.intentflow.core.ast.AggregateFunction;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public record CassandraAggregate(
        AggregateFunction function,
        List<Function<Row, @Nullable Object>> groups,
        @Nullable Function<Row, @Nullable Object> argument,
        Comparator<List<@Nullable Object>> order) {

    public CassandraAggregate {
        groups = List.copyOf(groups);
    }
}
