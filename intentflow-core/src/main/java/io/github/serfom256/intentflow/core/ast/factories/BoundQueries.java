package io.github.serfom256.intentflow.core.ast.factories;

import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.DslLiteral;
import io.github.serfom256.intentflow.core.ast.impl.DefaultBoundQuery;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BoundQueries {

    public static BoundQuery of(List<String> segments, List<DslLiteral> parameters) {
        return new DefaultBoundQuery(segments, parameters);
    }

    public static BoundQuery of(List<String> segments, List<DslLiteral> parameters, List<String> optimizations) {
        return new DefaultBoundQuery(segments, parameters, optimizations);
    }

    public static BoundQuery of(String sql) {
        return new DefaultBoundQuery(List.of(sql), List.of());
    }
}
