package io.github.serfom256.intentflow.cassandra;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import io.github.serfom256.intentflow.core.ast.AggregateFunction;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

public record CassandraAggregate(
        AggregateFunction function,
        List<CqlIdentifier> groupColumns,
        @Nullable CqlIdentifier column,
        Comparator<List<@Nullable Object>> order) {

    public CassandraAggregate {
        groupColumns = List.copyOf(groupColumns);
    }
}
