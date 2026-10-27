package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import org.jspecify.annotations.Nullable;

import java.net.URI;

/**
 * Builds the client of one LLM provider module for a model; the starter calls
 * it for the primary and the fallback model and puts the circuit breaker and
 * concurrency limit around what it returns.
 */
@FunctionalInterface
interface LlmClientFactory {

    LlmClient create(@Nullable URI baseUrl, String model, String apiKey);
}
