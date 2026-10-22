package io.github.serfom256.intentflow.llm.openai;

import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class OpenAiLlmClients {

    public static LlmClient of(URI baseUrl, String model, String apiKey, Duration timeout) {
        return new OpenAiCompatibleLlmClient(baseUrl, model, apiKey, timeout);
    }

    public static LlmClient of(URI baseUrl, String model, String apiKey, Duration timeout, Clock clock) {
        return new OpenAiCompatibleLlmClient(baseUrl, model, apiKey, timeout, clock);
    }

    public static LlmClient of(URI baseUrl, String model, String apiKey, Duration timeout, Clock clock,
            LlmHttpOptions options) {
        return new OpenAiCompatibleLlmClient(baseUrl, model, apiKey, timeout, clock, options);
    }
}
