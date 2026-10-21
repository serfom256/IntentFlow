package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.IntentFlowException;

import java.time.Duration;

public class RateLimitExceededException extends IntentFlowException {

    private final Duration retryAfter;

    public RateLimitExceededException(String message, Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
