package io.github.serfom256.intentflow.core.catalog.impl;

import io.github.serfom256.intentflow.core.catalog.ServiceDescription;

public record DefaultServiceDescription(String value) implements ServiceDescription {

    public DefaultServiceDescription {
        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    "ServiceDescription must be non-blank — omit (null) instead");
        }
    }
}
