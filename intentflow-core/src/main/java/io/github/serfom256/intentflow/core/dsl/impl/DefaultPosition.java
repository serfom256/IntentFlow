package io.github.serfom256.intentflow.core.dsl.impl;

import io.github.serfom256.intentflow.core.dsl.Position;

public record DefaultPosition(int offset, int line, int column) implements Position {

    public DefaultPosition {
        if (offset < 0) {
            throw new IllegalArgumentException("Position offset must be >= 0");
        }
        if (line < 1) {
            throw new IllegalArgumentException("Position line must be >= 1");
        }
        if (column < 1) {
            throw new IllegalArgumentException("Position column must be >= 1");
        }
    }
}
