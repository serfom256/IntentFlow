package io.github.serfom256.intentflow.core.ast;

public record CastExpr(ValueExpr value, CastType type) implements ValueExpr {
}
