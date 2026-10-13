package io.github.serfom256.intentflow.core.ast;

import java.util.Locale;
import java.util.Optional;

public enum AggregateFunction {
    COUNT,
    SUM,
    AVG,
    MIN,
    MAX;

    public String keyword() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<AggregateFunction> fromKeyword(String word) {
        for (AggregateFunction f : values()) {
            if (f.keyword().equals(word.toLowerCase(Locale.ROOT))) {
                return Optional.of(f);
            }
        }
        return Optional.empty();
    }
}
