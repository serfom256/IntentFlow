package io.github.serfom256.intentflow.core.api;

import java.util.Optional;

public interface IntentRequest {

    UserText text();

    /**
     * Who asks. Empty lets the pipeline use its default context, which the Spring
     * Boot starter reads from Spring Security when it is present.
     */
    default Optional<IntentContext> context() {
        return Optional.empty();
    }

    /**
     * Whether the response should say where the next page starts
     * ({@link IntentResponse#nextCursor()}). Set by {@code IntentRequests.page}.
     */
    default boolean paged() {
        return false;
    }

    /**
     * The {@link IntentResponse#nextCursor()} of the previous page; empty for the
     * first page. The same question must be asked again with it.
     */
    default Optional<String> cursor() {
        return Optional.empty();
    }
}
