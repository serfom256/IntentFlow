package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ErrorCode;
import io.github.serfom256.intentflow.core.IntentFlowException;

import java.time.Duration;
import java.util.Map;

public class RateLimitExceededException extends IntentFlowException {

    private final Duration retryAfter;

    public RateLimitExceededException(String message, Duration retryAfter) {
        super(ErrorCode.RATE_LIMITED, message, Map.of("retryAfterSeconds", Math.max(1, retryAfter.toSeconds())));
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
