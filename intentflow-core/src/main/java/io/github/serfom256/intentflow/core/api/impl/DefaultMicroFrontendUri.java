package io.github.serfom256.intentflow.core.api.impl;

import io.github.serfom256.intentflow.core.api.MicroFrontendUri;

public record DefaultMicroFrontendUri(String value) implements MicroFrontendUri {

    public DefaultMicroFrontendUri {
        if (value.isBlank()) {
            throw new IllegalArgumentException("MicroFrontendUri must be non-blank");
        }
    }
}
