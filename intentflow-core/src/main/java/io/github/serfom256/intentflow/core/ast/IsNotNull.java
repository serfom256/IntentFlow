package io.github.serfom256.intentflow.core.ast;

import io.github.serfom256.intentflow.core.catalog.LogicalFieldName;

public record IsNotNull(LogicalFieldName field) implements FilterExpr {
}
