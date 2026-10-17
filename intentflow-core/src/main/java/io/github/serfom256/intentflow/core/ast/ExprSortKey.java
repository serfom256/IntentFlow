package io.github.serfom256.intentflow.core.ast;

import io.github.serfom256.intentflow.core.catalog.LogicalFieldName;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import io.github.serfom256.intentflow.core.catalog.factories.LogicalFieldNames;

public record ExprSortKey(ValueExpr expression, SortKey.Direction direction) implements SortKey {

    @Override
    public LogicalFieldName field() {
        return LogicalFieldNames.of(Expressions.print(expression));
    }
}
