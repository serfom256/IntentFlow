package io.github.serfom256.intentflow.core.ast;

public sealed interface DslLiteral permits TextLiteral, NumberLiteral, BoolLiteral, NullLiteral {
}
