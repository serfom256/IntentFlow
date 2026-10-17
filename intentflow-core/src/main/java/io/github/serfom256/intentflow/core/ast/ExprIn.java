package io.github.serfom256.intentflow.core.ast;

import java.util.List;

public record ExprIn(ValueExpr value, List<DslLiteral> values) implements FilterExpr {

    public ExprIn {
        values = List.copyOf(values);
        if (values.isEmpty()) {
            throw new IllegalArgumentException("In values must be non-empty");
        }
    }
}
