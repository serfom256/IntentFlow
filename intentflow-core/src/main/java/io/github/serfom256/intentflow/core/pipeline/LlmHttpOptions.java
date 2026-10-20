package io.github.serfom256.intentflow.core.pipeline;

import org.jspecify.annotations.Nullable;

import java.net.ProxySelector;
import java.time.Duration;
import java.util.Map;

public record LlmHttpOptions(String apiKeyHeader, Map<String, String> headers, @Nullable ProxySelector proxy,
        int maxRetries, Duration retryBackoff) {

    public static final LlmHttpOptions DEFAULTS = new LlmHttpOptions("Authorization", Map.of(), null, 2,
            Duration.ofMillis(500));

    public LlmHttpOptions {
        headers = Map.copyOf(headers);
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must not be negative: " + maxRetries);
        }
        if (retryBackoff.isNegative()) {
            throw new IllegalArgumentException("retryBackoff must not be negative: " + retryBackoff);
        }
    }
}
