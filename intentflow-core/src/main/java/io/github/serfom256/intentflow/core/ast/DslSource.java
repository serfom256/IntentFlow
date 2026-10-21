package io.github.serfom256.intentflow.core.ast;

import io.github.serfom256.intentflow.core.api.TokenUsage;
import org.jspecify.annotations.Nullable;

public interface DslSource {

    String value();

    /**
     * Tokens the LLM call that wrote this query used; {@link TokenUsage#NONE} when
     * not reported.
     */
    default TokenUsage usage() {
        return TokenUsage.NONE;
    }

    /** The model that wrote this query, when the client knows it. */
    default @Nullable String model() {
        return null;
    }
}
