package io.github.serfom256.intentflow.core.ast.impl;

import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.DslLiteral;

import java.util.List;

public record DefaultBoundQuery(List<String> segments, List<DslLiteral> parameters) implements BoundQuery {

    public DefaultBoundQuery {
        segments = List.copyOf(segments);
        parameters = List.copyOf(parameters);
        if (segments.size() != parameters.size() + 1) {
            throw new IllegalArgumentException("expected " + (parameters.size() + 1)
                    + " segments for " + parameters.size() + " parameters, got " + segments.size());
        }
    }
}
