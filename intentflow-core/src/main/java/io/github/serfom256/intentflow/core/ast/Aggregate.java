package io.github.serfom256.intentflow.core.ast;

import org.jspecify.annotations.Nullable;

import java.util.List;

public record Aggregate(AggregateFunction function, @Nullable ValueExpr argument, List<ValueExpr> groupBy) {

    public Aggregate {
        groupBy = List.copyOf(groupBy);
        if ((function == AggregateFunction.COUNT) != (argument == null)) {
            throw new IllegalArgumentException(function == AggregateFunction.COUNT ? "count takes no field"
                    : function.keyword() + " needs a field");
        }
    }

    public List<String> groupNames() {
        return groupBy.stream().map(Expressions::print).toList();
    }
}
