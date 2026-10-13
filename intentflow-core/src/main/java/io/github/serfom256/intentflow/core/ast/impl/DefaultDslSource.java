package io.github.serfom256.intentflow.core.ast.impl;

import io.github.serfom256.intentflow.core.ast.DslSource;

public record DefaultDslSource(String value) implements DslSource {

    public DefaultDslSource {
        if (value.isBlank()) {
            throw new IllegalArgumentException("DslSource must be non-blank");
        }
    }
}
