package io.github.serfom256.intentflow.core.ast;

import java.util.Locale;
import java.util.Optional;

public enum ExprFunction {
    COALESCE,
    YEAR,
    MONTH,
    DAY,
    HOUR,
    DATE_TRUNC,
    LENGTH,
    LOWER,
    UPPER,
    ROUND,
    ABS;

    public String keyword() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<ExprFunction> fromKeyword(String word) {
        for (ExprFunction f : values()) {
            if (f.keyword().equals(word.toLowerCase(Locale.ROOT))) {
                return Optional.of(f);
            }
        }
        return Optional.empty();
    }
}
