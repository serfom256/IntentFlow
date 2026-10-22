package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ErrorCode;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

public class LlmUnavailableException extends LlmException {

    private final @Nullable Duration retryAfter;

    public LlmUnavailableException(String message, @Nullable Duration retryAfter) {
        super(ErrorCode.LLM_UNAVAILABLE, message,
                retryAfter == null ? Map.of() : Map.of("retryAfterSeconds", Math.max(1, retryAfter.toSeconds())), null);
        this.retryAfter = retryAfter;
    }

    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
