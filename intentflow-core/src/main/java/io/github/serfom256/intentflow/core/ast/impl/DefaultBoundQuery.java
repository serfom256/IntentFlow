package io.github.serfom256.intentflow.core.ast.impl;

import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.DslLiteral;

import java.util.List;

public record DefaultBoundQuery(List<String> segments, List<DslLiteral> parameters, List<String> optimizations)
        implements BoundQuery {

    public DefaultBoundQuery(List<String> segments, List<DslLiteral> parameters) {
        this(segments, parameters, List.of());
    }

    public DefaultBoundQuery {
        segments = List.copyOf(segments);
        parameters = List.copyOf(parameters);
        optimizations = List.copyOf(optimizations);
        if (segments.size() != parameters.size() + 1) {
            throw new IllegalArgumentException("expected " + (parameters.size() + 1)
                    + " segments for " + parameters.size() + " parameters, got " + segments.size());
        }
    }
}
