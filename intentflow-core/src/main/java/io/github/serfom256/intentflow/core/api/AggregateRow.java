package io.github.serfom256.intentflow.core.api;

import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public record AggregateRow(List<@Nullable Object> group, @Nullable Object value) {

    public AggregateRow {
        group = Collections.unmodifiableList(new java.util.ArrayList<>(group));
    }
}
