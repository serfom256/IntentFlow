package io.github.serfom256.intentflow.core.api.impl;

import io.github.serfom256.intentflow.core.api.IntentTrace;

import java.util.Collections;
import java.util.List;

public record DefaultIntentTrace(String dsl, boolean cached, String query, List<Object> parameters)
        implements IntentTrace {

    public DefaultIntentTrace {
        parameters = Collections.unmodifiableList(new java.util.ArrayList<>(parameters));
    }
}
