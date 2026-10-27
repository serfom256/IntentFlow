package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.api.IntentContext;

/**
 * Limits how many questions that need the LLM a caller may ask, so one user or
 * tenant cannot use up the budget. The pipeline calls {@link #acquire} once per
 * question that misses the DSL cache, before the first LLM call; cached answers
 * and repairs of the same question are not counted. Refuse by throwing
 * {@link RateLimitExceededException}. The built-in limiter keeps its counts in
 * memory, per instance; one backed by a shared store makes the limit hold
 * across instances.
 */
@FunctionalInterface
public interface LlmRateLimiter {

    LlmRateLimiter NONE = caller -> {
    };

    void acquire(IntentContext caller);
}
