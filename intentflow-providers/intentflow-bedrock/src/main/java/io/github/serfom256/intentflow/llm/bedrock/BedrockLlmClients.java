package io.github.serfom256.intentflow.llm.bedrock;

import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClientBuilder;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BedrockLlmClients {

    public static LlmClient of(String model, BedrockSettings settings, Duration timeout, int maxRetries, Clock clock) {
        return of(model, settings, DefaultCredentialsProvider.builder().build(), null, timeout, maxRetries, clock);
    }

    public static LlmClient of(String model, BedrockSettings settings, AwsCredentialsProvider credentials,
            @Nullable URI endpoint, Duration timeout, int maxRetries, Clock clock) {
        BedrockRuntimeClientBuilder client = BedrockRuntimeClient.builder().credentialsProvider(credentials)
                .overrideConfiguration(ClientOverrideConfiguration.builder().apiCallAttemptTimeout(timeout)
                        .retryStrategy(retry -> retry.maxAttempts(maxRetries + 1)).build());
        if (settings.region() != null) {
            client.region(Region.of(settings.region()));
        }
        if (endpoint != null) {
            client.endpointOverride(endpoint);
        }
        return new BedrockLlmClient(client.build(), credentials, model, settings, clock);
    }
}
