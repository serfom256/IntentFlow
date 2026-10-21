package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.intentflow.core.pipeline.LlmClient;

/**
 * Puts a circuit breaker named {@code name} around an LLM client the starter
 * builds: Resilience4j's when the
 * application has a {@code CircuitBreakerRegistry}, otherwise the built-in one.
 */
@FunctionalInterface
interface LlmCircuitBreaker {

    LlmClient wrap(LlmClient client, String name);
}
