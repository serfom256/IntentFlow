package io.github.serfom256.intentflow.core.pipeline.factories;

import io.github.serfom256.intentflow.core.api.UserText;
import io.github.serfom256.intentflow.core.ast.DslSource;
import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import io.github.serfom256.intentflow.core.pipeline.impl.OpenAiCompatibleLlmClient;
import io.github.serfom256.intentflow.core.pipeline.impl.StubLlmClient;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LlmClients {

    public static LlmClient stub(Map<UserText, DslSource> responses) {
        return new StubLlmClient(responses);
    }

    public static LlmClient openAiCompatible(URI baseUrl, String model, String apiKey, Duration timeout) {
        return new OpenAiCompatibleLlmClient(baseUrl, model, apiKey, timeout);
    }

    public static LlmClient openAiCompatible(URI baseUrl, String model, String apiKey, Duration timeout,
                                             Clock clock) {
        return new OpenAiCompatibleLlmClient(baseUrl, model, apiKey, timeout, clock);
    }
}
