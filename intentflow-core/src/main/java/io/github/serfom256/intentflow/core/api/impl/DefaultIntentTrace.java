package io.github.serfom256.intentflow.core.api.impl;

import io.github.serfom256.intentflow.core.api.IntentTrace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record DefaultIntentTrace(String dsl, boolean cached, String query, List<Object> parameters,
        List<String> optimizations, int repairs) implements IntentTrace {

    public DefaultIntentTrace(String dsl, boolean cached, String query, List<Object> parameters) {
        this(dsl, cached, query, parameters, List.of(), 0);
    }

    public DefaultIntentTrace(String dsl, boolean cached, String query, List<Object> parameters,
            List<String> optimizations) {
        this(dsl, cached, query, parameters, optimizations, 0);
    }

    public DefaultIntentTrace {
        parameters = Collections.unmodifiableList(new ArrayList<>(parameters));
        optimizations = List.copyOf(optimizations);
    }
}
