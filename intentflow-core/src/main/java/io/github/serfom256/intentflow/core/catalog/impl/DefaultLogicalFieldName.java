package io.github.serfom256.intentflow.core.catalog.impl;

import io.github.serfom256.intentflow.core.catalog.LogicalFieldName;

public record DefaultLogicalFieldName(String value) implements LogicalFieldName {

    public DefaultLogicalFieldName {
        if (value.isBlank()) {
            throw new IllegalArgumentException("LogicalFieldName must be non-blank");
        }
    }
}
