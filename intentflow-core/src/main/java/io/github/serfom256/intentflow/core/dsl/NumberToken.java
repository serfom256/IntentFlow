package io.github.serfom256.intentflow.core.dsl;

import java.math.BigDecimal;

public record NumberToken(BigDecimal value, Position position) implements Token {
}
