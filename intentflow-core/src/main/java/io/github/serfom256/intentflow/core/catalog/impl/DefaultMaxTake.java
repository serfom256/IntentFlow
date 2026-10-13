package io.github.serfom256.intentflow.core.catalog.impl;

import io.github.serfom256.intentflow.core.catalog.MaxTake;

public record DefaultMaxTake(int value) implements MaxTake {

    public DefaultMaxTake {
        if (value <= 0) {
            throw new IllegalArgumentException("MaxTake must be > 0");
        }
    }
}
