package io.github.serfom256.intentflow.core.ast.impl;

import io.github.serfom256.intentflow.core.api.TokenUsage;
import io.github.serfom256.intentflow.core.ast.DslSource;
import org.jspecify.annotations.Nullable;

public record DefaultDslSource(String value, TokenUsage usage, @Nullable String model) implements DslSource {

    public DefaultDslSource(String value) {
        this(value, TokenUsage.NONE, null);
    }

    public DefaultDslSource {
        if (value.isBlank()) {
            throw new IllegalArgumentException("DslSource must be non-blank");
        }
    }
}
