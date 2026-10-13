package io.github.serfom256.intentflow.core.ast;

public sealed interface FilterExpr
        permits And, Or, Not, Compare, In, IsNull, IsNotNull {
}
