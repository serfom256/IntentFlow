package io.github.serfom256.intentflow.llm.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.net.Proxy;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AnthropicLlmClients {

    public static LlmClient of(@Nullable URI baseUrl, String model, String apiKey, AnthropicSettings settings,
            Duration timeout, int maxRetries, @Nullable Proxy proxy, Clock clock) {
        AnthropicOkHttpClient.Builder client = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(timeout)
                .maxRetries(maxRetries);
        if (baseUrl != null) {
            client.baseUrl(baseUrl.toString());
        }
        if (proxy != null) {
            client.proxy(proxy);
        }
        return of(client.build(), model, settings, clock);
    }

    public static LlmClient of(AnthropicClient client, String model, AnthropicSettings settings, Clock clock) {
        return new AnthropicLlmClient(client, model, settings.maxTokens(), settings.effort(),
                settings.refusalFallback(), clock);
    }
}
