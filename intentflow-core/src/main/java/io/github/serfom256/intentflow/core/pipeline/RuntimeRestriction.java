package io.github.serfom256.intentflow.core.pipeline;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public record RuntimeRestriction(List<String> segments, List<Object> values) {

    public RuntimeRestriction {
        segments = List.copyOf(segments);
        values = List.copyOf(values);
        if (segments.size() != values.size() + 1) {
            throw new IllegalArgumentException("expected " + (values.size() + 1) + " segments for " + values.size()
                    + " values, got " + segments.size());
        }
        if (values.stream().anyMatch(Optional.class::isInstance)) {
            throw new IllegalArgumentException("bind the value inside the Optional, not the Optional itself");
        }
    }

    public static RuntimeRestriction of(String sql) {
        return new RuntimeRestriction(List.of(sql), List.of());
    }

    public static RuntimeRestriction equalTo(String column, @Nullable Object value) {
        if (value == null) {
            return of("1 = 0");
        }
        return new RuntimeRestriction(List.of(column + " = ", ""), List.of(value));
    }

    public static RuntimeRestriction equalTo(String column, Optional<?> value) {
        return equalTo(column, value.orElse(null));
    }

    public static RuntimeRestriction in(String column, Collection<?> values) {
        if (values.isEmpty()) {
            return of("1 = 0");
        }
        List<String> segments = new ArrayList<>();
        segments.add(column + " IN (");
        for (int i = 1; i < values.size(); i++) {
            segments.add(", ");
        }
        segments.add(")");
        return new RuntimeRestriction(segments, List.copyOf(values));
    }
}
