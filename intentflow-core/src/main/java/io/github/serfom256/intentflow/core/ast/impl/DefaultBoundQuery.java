package io.github.serfom256.intentflow.core.ast.impl;

import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.DslLiteral;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public record DefaultBoundQuery(List<String> segments, List<DslLiteral> parameters, List<String> optimizations,
        Map<Integer, Object> typedValues) implements BoundQuery {

    public DefaultBoundQuery(List<String> segments, List<DslLiteral> parameters) {
        this(segments, parameters, List.of(), Map.of());
    }

    public DefaultBoundQuery(List<String> segments, List<DslLiteral> parameters, List<String> optimizations) {
        this(segments, parameters, optimizations, Map.of());
    }

    @Override
    public List<@Nullable Object> values() {
        List<@Nullable Object> values = BoundQuery.super.values();
        typedValues.forEach(values::set);
        return values;
    }

    public DefaultBoundQuery {
        segments = List.copyOf(segments);
        parameters = List.copyOf(parameters);
        optimizations = List.copyOf(optimizations);
        typedValues = Map.copyOf(typedValues);
        if (segments.size() != parameters.size() + 1) {
            throw new IllegalArgumentException("expected " + (parameters.size() + 1)
                    + " segments for " + parameters.size() + " parameters, got " + segments.size());
        }
    }
}
