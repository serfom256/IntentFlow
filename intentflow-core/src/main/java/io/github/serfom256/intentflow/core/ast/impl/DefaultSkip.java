package io.github.serfom256.intentflow.core.ast.impl;

import io.github.serfom256.intentflow.core.ast.Skip;

public record DefaultSkip(int value) implements Skip {

    public DefaultSkip {
        if (value < 0) {
            throw new IllegalArgumentException("Skip must be >= 0");
        }
    }
}
