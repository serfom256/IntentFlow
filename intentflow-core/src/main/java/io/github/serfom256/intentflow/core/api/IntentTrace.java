package io.github.serfom256.intentflow.core.api;

import java.util.List;

/** How a request was answered, for debugging and logs. */
public interface IntentTrace {

    /**
     * The query language text the LLM produced, e.g.
     * {@code products | filter price < 100}.
     */
    String dsl();

    /** Whether {@link #dsl()} came from the cache instead of the LLM. */
    boolean cached();

    /** The statement sent to the database, with placeholders. */
    String query();

    /** Bound parameter values, in placeholder order. */
    List<Object> parameters();

    /**
     * Optimizations applied to the statement, e.g. {@code date-ranges}; empty when
     * the SQL is unchanged.
     */
    default List<String> optimizations() {
        return List.of();
    }

    /**
     * How many times the LLM was asked to correct a rejected query before this one
     * was accepted.
     */
    default int repairs() {
        return 0;
    }

    /**
     * Tokens the LLM used for this request, summed over the first answer and any
     * repairs; {@link TokenUsage#NONE}
     * when the query came from the cache or the provider did not report usage.
     */
    default TokenUsage tokens() {
        return TokenUsage.NONE;
    }
}
