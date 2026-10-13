package io.github.serfom256.intentflow.core.catalog.impl;

import io.github.serfom256.intentflow.core.catalog.ServiceName;

public record DefaultServiceName(String value) implements ServiceName {

    public DefaultServiceName {
        if (value.isBlank()) {
            throw new IllegalArgumentException("ServiceName must be non-blank");
        }
    }
}
