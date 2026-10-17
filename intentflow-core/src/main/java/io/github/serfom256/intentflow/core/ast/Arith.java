package io.github.serfom256.intentflow.core.ast;

public record Arith(ArithOp op, ValueExpr left, ValueExpr right) implements ValueExpr {
}
