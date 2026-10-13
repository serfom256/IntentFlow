package io.github.serfom256.intentflow.core.ast.impl;

import io.github.serfom256.intentflow.core.ast.Take;

public record DefaultTake(int value) implements Take {

    public DefaultTake {
        if (value <= 0) {
            throw new IllegalArgumentException("Take must be > 0");
        }
    }
}
