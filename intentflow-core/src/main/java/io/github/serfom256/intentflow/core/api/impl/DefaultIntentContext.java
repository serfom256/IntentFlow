package io.github.serfom256.intentflow.core.api.impl;

import io.github.serfom256.intentflow.core.api.IntentContext;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record DefaultIntentContext(Optional<String> principal, Optional<String> tenant, Set<String> roles,
        Map<String, Object> attributes) implements IntentContext {

    public DefaultIntentContext {
        roles = Set.copyOf(roles);
        attributes = Map.copyOf(attributes);
    }
}
