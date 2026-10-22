package io.github.serfom256.intentflow.llm.azure;

import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import io.github.serfom256.intentflow.llm.openai.LlmHttpOptions;
import io.github.serfom256.intentflow.llm.openai.OpenAiCompatibleLlmClient;
import io.github.serfom256.intentflow.llm.openai.RequestAuthorizer;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AzureOpenAiLlmClients {

    private static final String IDENTITY = "com.azure.identity.DefaultAzureCredentialBuilder";

    public static LlmClient of(URI endpoint, String deployment, String apiKey, Duration timeout, Clock clock,
            LlmHttpOptions options) {
        return of(endpoint, deployment, apiKey.isEmpty() ? entraId() : RequestAuthorizer.apiKey(apiKey, "api-key"),
                timeout, clock, options);
    }

    public static LlmClient of(URI endpoint, String deployment, RequestAuthorizer authorizer, Duration timeout,
            Clock clock, LlmHttpOptions options) {
        return OpenAiCompatibleLlmClient.builder(v1(endpoint), deployment).authorizer(authorizer).timeout(timeout)
                .clock(clock).options(options).build();
    }

    static URI v1(URI endpoint) {
        String base = endpoint.toString().replaceAll("/+$", "");
        return URI.create(base.endsWith("/openai/v1") ? base : base + "/openai/v1");
    }

    private static RequestAuthorizer entraId() {
        try {
            Class.forName(IDENTITY, false, AzureOpenAiLlmClients.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Azure OpenAI without an API key signs in with Entra ID: add "
                    + "com.azure:azure-identity to the classpath, or set the API key.", e);
        }
        return new EntraIdAuthorizer();
    }
}
