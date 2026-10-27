package io.github.serfom256.intentflow.core.ast.impl;

import io.github.serfom256.intentflow.core.api.TokenUsage;
import io.github.serfom256.intentflow.core.ast.DslSource;

import java.util.Optional;

public record DefaultDslSource(String value, TokenUsage usage, Optional<String> model) implements DslSource {

    public DefaultDslSource(String value) {
        this(value, TokenUsage.NONE, Optional.empty());
    }

    public DefaultDslSource {
        if (value.isBlank()) {
            throw new IllegalArgumentException("DslSource must be non-blank");
        }
    }
}
