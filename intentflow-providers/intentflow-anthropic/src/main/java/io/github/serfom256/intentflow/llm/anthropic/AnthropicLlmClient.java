package io.github.serfom256.intentflow.llm.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Usage;
import io.github.serfom256.intentflow.core.api.TokenUsage;
import io.github.serfom256.intentflow.core.api.UserText;
import io.github.serfom256.intentflow.core.ast.DslSource;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import io.github.serfom256.intentflow.core.pipeline.LlmException;
import io.github.serfom256.intentflow.core.pipeline.LlmPrompts;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class AnthropicLlmClient implements LlmClient {

    private static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

    private final AnthropicClient client;
    private final String model;
    private final long maxTokens;
    private final @Nullable String effort;
    private final boolean refusalFallback;
    private final Clock clock;

    public AnthropicLlmClient(AnthropicClient client, String model, long maxTokens, @Nullable String effort,
            boolean refusalFallback, Clock clock) {
        this.client = client;
        this.model = model;
        this.maxTokens = maxTokens;
        this.effort = effort;
        this.refusalFallback = refusalFallback;
        this.clock = clock;
    }

    @Override
    public DslSource generateDsl(UserText normalizedText, ServiceCatalog catalog) {
        return complete(catalog, builder -> builder.addUserMessage(normalizedText.value()));
    }

    @Override
    public DslSource repairDsl(UserText normalizedText, ServiceCatalog catalog, DslSource failed, String error) {
        return complete(catalog, builder -> builder.addUserMessage(normalizedText.value())
                .addAssistantMessage(failed.value())
                .addUserMessage(LlmPrompts.repair(error)));
    }

    @Override
    public void ping() {
        try {
            client.models().retrieve(model);
        } catch (AnthropicException e) {
            throw new LlmException("Anthropic API check failed: " + e.getMessage(), e);
        }
    }

    private DslSource complete(ServiceCatalog catalog, Consumer<MessageCreateParams.Builder> messages) {
        MessageCreateParams.Builder request = MessageCreateParams.builder()
                .model(model)
                .maxTokens(maxTokens)
                .systemOfTextBlockParams(List.of(TextBlockParam.builder()
                        .text(LlmPrompts.system(catalog, LocalDate.now(clock)))
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build()));
        messages.accept(request);
        if (effort != null) {
            request.outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.of(effort)).build());
        }
        if (refusalFallback) {
            request.putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
                    .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }
        Message message;
        try {
            message = client.messages().create(request.build());
        } catch (AnthropicException e) {
            throw new LlmException("Anthropic request failed: " + e.getMessage(), e);
        }
        if (message.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
            String category = message.stopDetails().flatMap(details -> details.category())
                    .map(Object::toString).orElse("unspecified");
            throw new LlmException("Claude declined to translate the question (" + category + ")");
        }
        String text = message.content().stream().flatMap(block -> block.text().stream())
                .map(block -> block.text()).collect(Collectors.joining());
        String dsl = LlmPrompts.answer(text);
        if (dsl.isEmpty()) {
            throw new LlmException("LLM returned an empty answer");
        }
        return DslSources.of(dsl, usage(message.usage()), message.model().asString());
    }

    private static TokenUsage usage(Usage usage) {
        long prompt = usage.inputTokens() + usage.cacheReadInputTokens().orElse(0L)
                + usage.cacheCreationInputTokens().orElse(0L);
        return new TokenUsage(prompt, usage.outputTokens());
    }
}
