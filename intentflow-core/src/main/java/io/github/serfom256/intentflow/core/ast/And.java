package io.github.serfom256.intentflow.core.ast;

public record And(FilterExpr left, FilterExpr right) implements FilterExpr {
}
