package io.github.serfom256.intentflow.core.api;

import org.jspecify.annotations.Nullable;

import java.util.List;

public record Aggregation(String function, @Nullable String field, List<String> groupBy, List<AggregateRow> rows) {

    public Aggregation {
        groupBy = List.copyOf(groupBy);
        rows = List.copyOf(rows);
    }
}
