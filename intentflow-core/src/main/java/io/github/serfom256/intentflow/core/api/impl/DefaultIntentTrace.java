package io.github.serfom256.intentflow.core.api.impl;

import io.github.serfom256.intentflow.core.api.IntentTrace;
import io.github.serfom256.intentflow.core.api.TokenUsage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record DefaultIntentTrace(String dsl, boolean cached, String query, List<Object> parameters,
        List<String> optimizations, int repairs, TokenUsage tokens) implements IntentTrace {

    public DefaultIntentTrace(String dsl, boolean cached, String query, List<Object> parameters) {
        this(dsl, cached, query, parameters, List.of(), 0, TokenUsage.NONE);
    }

    public DefaultIntentTrace(String dsl, boolean cached, String query, List<Object> parameters,
            List<String> optimizations) {
        this(dsl, cached, query, parameters, optimizations, 0, TokenUsage.NONE);
    }

    public DefaultIntentTrace(String dsl, boolean cached, String query, List<Object> parameters,
            List<String> optimizations, int repairs) {
        this(dsl, cached, query, parameters, optimizations, repairs, TokenUsage.NONE);
    }

    public DefaultIntentTrace {
        parameters = Collections.unmodifiableList(new ArrayList<>(parameters));
        optimizations = List.copyOf(optimizations);
    }
}
