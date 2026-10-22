package io.github.serfom256.intentflow.llm.vertex;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GeminiLlmClients {

    public static LlmClient vertex(String project, String location, String model, int maxTokens, Duration timeout,
            Clock clock) {
        Client client = Client.builder().vertexAI(true).project(project).location(location)
                .httpOptions(httpOptions(null, timeout)).build();
        return new GeminiLlmClient(client, model, maxTokens, clock);
    }

    public static LlmClient apiKey(@Nullable URI baseUrl, String apiKey, String model, int maxTokens,
            Duration timeout, Clock clock) {
        Client client = Client.builder().apiKey(apiKey).httpOptions(httpOptions(baseUrl, timeout)).build();
        return new GeminiLlmClient(client, model, maxTokens, clock);
    }

    private static HttpOptions httpOptions(@Nullable URI baseUrl, Duration timeout) {
        HttpOptions.Builder options = HttpOptions.builder().timeout((int) timeout.toMillis());
        if (baseUrl != null) {
            options.baseUrl(baseUrl.toString());
        }
        return options.build();
    }
}
