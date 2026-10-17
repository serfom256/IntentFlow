package io.github.serfom256.intentflow.core.ast;

public record ExprCompare(ValueExpr left, ComparisonOp op, ValueExpr right) implements FilterExpr {
}
