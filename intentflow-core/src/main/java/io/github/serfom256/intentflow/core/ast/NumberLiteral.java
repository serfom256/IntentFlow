package io.github.serfom256.intentflow.core.ast;

import java.math.BigDecimal;

public record NumberLiteral(BigDecimal value) implements DslLiteral {
}
