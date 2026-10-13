package io.github.serfom256.intentflow.core.catalog.impl;

import io.github.serfom256.intentflow.core.catalog.TableName;

public record DefaultTableName(String value) implements TableName {

    public DefaultTableName {
        if (value.isBlank()) {
            throw new IllegalArgumentException("TableName must be non-blank");
        }
    }
}
