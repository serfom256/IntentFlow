package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.api.IntentRequest;
import io.github.serfom256.intentflow.core.api.IntentResponse;

/**
 * Public entry point: natural-language request in, matching entities out.
 * normalize → cache / LLM → parse → validate → SQL → {@link QueryExecutor} → URI.
 */
public interface IntentPipeline {

    /**
     * Lets the LLM pick the service from the whole catalog. The response's
     * {@link IntentResponse#type()} tells which entity class came back.
     */
    IntentResponse<?> resolve(IntentRequest request);

    /**
     * Answers from the one service whose result type is {@code resultType}:
     * the LLM only sees that service, and anything else it produces is rejected.
     *
     * @throws IllegalArgumentException when no service maps to {@code resultType}
     */
    <T> IntentResponse<T> resolve(IntentRequest request, Class<T> resultType);
}
