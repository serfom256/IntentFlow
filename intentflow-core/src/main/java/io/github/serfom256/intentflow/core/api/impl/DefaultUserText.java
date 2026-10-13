package io.github.serfom256.intentflow.core.api.impl;

import io.github.serfom256.intentflow.core.api.UserText;

public record DefaultUserText(String value) implements UserText {

    public DefaultUserText {
        if (value.isBlank()) {
            throw new IllegalArgumentException("UserText must be non-blank");
        }
    }
}
