package io.github.serfom256.intentflow.core.api.impl;

import io.github.serfom256.intentflow.core.api.IntentContext;
import io.github.serfom256.intentflow.core.api.IntentRequest;
import io.github.serfom256.intentflow.core.api.UserText;
import org.jspecify.annotations.Nullable;

public record DefaultIntentRequest(UserText text, @Nullable IntentContext context, boolean paged,
        @Nullable String cursor) implements IntentRequest {

    public DefaultIntentRequest(UserText text) {
        this(text, null);
    }

    public DefaultIntentRequest(UserText text, @Nullable IntentContext context) {
        this(text, context, false, null);
    }
}
