package io.github.serfom256.intentflow.core.ast;

import io.github.serfom256.intentflow.core.catalog.LogicalFieldName;

import java.util.List;

public record In(LogicalFieldName field, List<DslLiteral> values) implements FilterExpr {

    public In {
        values = List.copyOf(values);
        if (values.isEmpty()) {
            throw new IllegalArgumentException("In values must be non-empty");
        }
    }
}
