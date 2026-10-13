package io.github.serfom256.intentflow.core.catalog.impl;

import io.github.serfom256.intentflow.core.catalog.ColumnName;

public record DefaultColumnName(String value) implements ColumnName {

    public DefaultColumnName {
        if (value.isBlank()) {
            throw new IllegalArgumentException("ColumnName must be non-blank");
        }
    }
}
