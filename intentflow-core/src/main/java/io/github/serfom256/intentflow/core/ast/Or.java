package io.github.serfom256.intentflow.core.ast;

public record Or(FilterExpr left, FilterExpr right) implements FilterExpr {
}
