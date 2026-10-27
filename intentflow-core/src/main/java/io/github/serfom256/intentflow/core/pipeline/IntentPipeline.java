package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.api.IntentRequest;
import io.github.serfom256.intentflow.core.api.IntentResponse;
import io.github.serfom256.intentflow.core.api.IntentTrace;

/**
 * Public entry point: natural-language request in, matching entities out.
 * normalize → cache / LLM → parse → validate → SQL → {@link QueryExecutor} →
 * URI.
 */
public interface IntentPipeline {

    /**
     * Lets the LLM pick the service from the whole catalog. The response's
     * {@link IntentResponse#type()} tells which entity class came back.
     */
    IntentResponse<?> resolve(IntentRequest request);

    /**
     * Answers from the one service whose result type is {@code resultType}: the LLM
     * only sees that service, and anything else it produces is rejected.
     *
     * @throws IllegalArgumentException when no service maps to {@code resultType}
     */
    <T> IntentResponse<T> resolve(IntentRequest request, Class<T> resultType);

    /**
     * Translates and compiles the request like {@link #resolve(IntentRequest)} but
     * does not run the query: the trace holds the DSL, whether it came from the
     * cache, the SQL and its parameters. A successful translation is cached, so a
     * following {@code resolve} of the same text does not call the LLM again.
     *
     * @throws UnsupportedRequestException when the LLM declines the request
     * @throws ValidationException         when the DSL does not fit the catalog
     */
    default IntentTrace explain(IntentRequest request) {
        throw new UnsupportedOperationException(getClass().getName() + " does not support explain");
    }

    /**
     * {@link #explain(IntentRequest)} limited to the service whose result type is
     * {@code resultType}.
     */
    default IntentTrace explain(IntentRequest request, Class<?> resultType) {
        throw new UnsupportedOperationException(getClass().getName() + " does not support explain");
    }
}
