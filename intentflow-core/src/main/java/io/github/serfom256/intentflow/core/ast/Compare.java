package io.github.serfom256.intentflow.core.ast;

import io.github.serfom256.intentflow.core.catalog.LogicalFieldName;

public record Compare(LogicalFieldName field, ComparisonOp op, DslLiteral value)
        implements FilterExpr {
}
