package io.github.serfom256.intentflow.core.api.impl;

import io.github.serfom256.intentflow.core.api.IntentTrace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record DefaultIntentTrace(String dsl, boolean cached, String query, List<Object> parameters,
        List<String> optimizations) implements IntentTrace {

    public DefaultIntentTrace(String dsl, boolean cached, String query, List<Object> parameters) {
        this(dsl, cached, query, parameters, List.of());
    }

    public DefaultIntentTrace {
        parameters = Collections.unmodifiableList(new ArrayList<>(parameters));
        optimizations = List.copyOf(optimizations);
    }
}
