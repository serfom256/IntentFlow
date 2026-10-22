package io.github.serfom256.intentflow.core.api;

import org.jspecify.annotations.Nullable;

public interface IntentRequest {

    UserText text();

    /**
     * Who asks. {@code null} lets the pipeline use its default context, which the
     * Spring Boot starter reads from
     * Spring Security when it is present.
     */
    default @Nullable IntentContext context() {
        return null;
    }

    /**
     * Whether the response should say where the next page starts
     * ({@link IntentResponse#nextCursor()}). Set by
     * {@code IntentRequests.page}.
     */
    default boolean paged() {
        return false;
    }

    /**
     * The {@link IntentResponse#nextCursor()} of the previous page, or {@code null}
     * for the first page. The same
     * question must be asked again with it.
     */
    default @Nullable String cursor() {
        return null;
    }
}
