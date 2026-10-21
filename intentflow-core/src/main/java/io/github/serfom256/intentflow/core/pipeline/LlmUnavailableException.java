package io.github.serfom256.intentflow.core.pipeline;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Optional;

public class LlmUnavailableException extends LlmException {

    private final @Nullable Duration retryAfter;

    public LlmUnavailableException(String message, @Nullable Duration retryAfter) {
        super(message);
        this.retryAfter = retryAfter;
    }

    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
