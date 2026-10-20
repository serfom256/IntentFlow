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
}
