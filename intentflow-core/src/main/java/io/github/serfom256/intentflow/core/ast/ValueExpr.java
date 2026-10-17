package io.github.serfom256.intentflow.core.ast;

/**
 * A value computed in a query: a field, a literal, a function call, arithmetic
 * or a cast.
 */
public sealed interface ValueExpr permits FieldRef, LiteralExpr, Call, Arith, CastExpr {
}
