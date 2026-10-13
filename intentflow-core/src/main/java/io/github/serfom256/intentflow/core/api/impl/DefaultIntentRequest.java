package io.github.serfom256.intentflow.core.api.impl;

import io.github.serfom256.intentflow.core.api.IntentRequest;
import io.github.serfom256.intentflow.core.api.UserText;

public record DefaultIntentRequest(UserText text) implements IntentRequest {
}
