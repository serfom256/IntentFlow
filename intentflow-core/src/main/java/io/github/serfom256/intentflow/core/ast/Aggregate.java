package io.github.serfom256.intentflow.core.ast;

import io.github.serfom256.intentflow.core.catalog.LogicalFieldName;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record Aggregate(AggregateFunction function, @Nullable LogicalFieldName field, List<LogicalFieldName> groupBy) {

    public Aggregate {
        groupBy = List.copyOf(groupBy);
        if ((function == AggregateFunction.COUNT) != (field == null)) {
            throw new IllegalArgumentException(function == AggregateFunction.COUNT
                    ? "count takes no field"
                    : function.keyword() + " needs a field");
        }
    }
}
