package io.github.serfom256.intentflow.llm.bedrock;

import io.github.serfom256.intentflow.core.api.TokenUsage;
import io.github.serfom256.intentflow.core.api.UserText;
import io.github.serfom256.intentflow.core.ast.DslSource;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import io.github.serfom256.intentflow.core.pipeline.LlmException;
import io.github.serfom256.intentflow.core.pipeline.LlmPrompts;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.CachePointBlock;
import software.amazon.awssdk.services.bedrockruntime.model.CachePointType;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class BedrockLlmClient implements LlmClient {

    private final BedrockRuntimeClient client;
    private final AwsCredentialsProvider credentials;
    private final String model;
    private final BedrockSettings settings;
    private final Clock clock;

    public BedrockLlmClient(BedrockRuntimeClient client, AwsCredentialsProvider credentials, String model,
            BedrockSettings settings, Clock clock) {
        this.client = client;
        this.credentials = credentials;
        this.model = model;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    public DslSource generateDsl(UserText normalizedText, ServiceCatalog catalog) {
        return complete(catalog, List.of(text(ConversationRole.USER, normalizedText.value())));
    }

    @Override
    public DslSource repairDsl(UserText normalizedText, ServiceCatalog catalog, DslSource failed, String error) {
        return complete(catalog, List.of(text(ConversationRole.USER, normalizedText.value()),
                text(ConversationRole.ASSISTANT, failed.value()),
                text(ConversationRole.USER, LlmPrompts.repair(error))));
    }

    @Override
    public void ping() {
        try {
            credentials.resolveCredentials();
        } catch (SdkException e) {
            throw new LlmException("No AWS credentials for Bedrock: " + e.getMessage(), e);
        }
    }

    private DslSource complete(ServiceCatalog catalog, List<Message> messages) {
        List<SystemContentBlock> system = new ArrayList<>();
        system.add(SystemContentBlock.fromText(LlmPrompts.system(catalog, LocalDate.now(clock))));
        if (settings.promptCaching()) {
            system.add(SystemContentBlock.fromCachePoint(CachePointBlock.builder().type(CachePointType.DEFAULT)
                    .build()));
        }
        InferenceConfiguration.Builder inference = InferenceConfiguration.builder().maxTokens(settings.maxTokens());
        if (settings.temperature() != null) {
            inference.temperature(settings.temperature());
        }
        ConverseResponse response;
        try {
            response = client.converse(ConverseRequest.builder().modelId(model).system(system).messages(messages)
                    .inferenceConfig(inference.build()).build());
        } catch (SdkException e) {
            throw new LlmException("Bedrock request failed: " + e.getMessage(), e);
        }
        String text = response.output().message().content().stream().map(ContentBlock::text)
                .filter(Objects::nonNull).collect(Collectors.joining());
        String dsl = LlmPrompts.answer(text);
        if (dsl.isEmpty()) {
            throw new LlmException("LLM returned an empty answer");
        }
        return DslSources.of(dsl, usage(response.usage()), model);
    }

    private static TokenUsage usage(software.amazon.awssdk.services.bedrockruntime.model.TokenUsage usage) {
        if (usage == null) {
            return TokenUsage.NONE;
        }
        long prompt = value(usage.inputTokens()) + value(usage.cacheReadInputTokens())
                + value(usage.cacheWriteInputTokens());
        return new TokenUsage(prompt, value(usage.outputTokens()));
    }

    private static long value(Integer count) {
        return count == null ? 0 : count;
    }

    private static Message text(ConversationRole role, String value) {
        return Message.builder().role(role).content(ContentBlock.fromText(value)).build();
    }
}
