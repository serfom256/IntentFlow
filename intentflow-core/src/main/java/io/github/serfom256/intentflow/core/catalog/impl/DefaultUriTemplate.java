package io.github.serfom256.intentflow.core.catalog.impl;

import io.github.serfom256.intentflow.core.catalog.UriTemplate;

public record DefaultUriTemplate(String value) implements UriTemplate {

    public DefaultUriTemplate {
        if (value.isBlank()) {
            throw new IllegalArgumentException("UriTemplate must be non-blank");
        }
    }
}
