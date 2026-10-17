package io.github.serfom256.intentflow.core.pipeline;

import java.util.List;

public record RuntimeRestriction(List<String> segments, List<Object> values) {

    public RuntimeRestriction {
        segments = List.copyOf(segments);
        values = List.copyOf(values);
        if (segments.size() != values.size() + 1) {
            throw new IllegalArgumentException("expected " + (values.size() + 1) + " segments for " + values.size()
                    + " values, got " + segments.size());
        }
    }

    public static RuntimeRestriction of(String sql) {
        return new RuntimeRestriction(List.of(sql), List.of());
    }
}
