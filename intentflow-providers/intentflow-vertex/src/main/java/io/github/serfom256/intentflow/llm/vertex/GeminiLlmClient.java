package io.github.serfom256.intentflow.llm.vertex;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.Part;
import io.github.serfom256.intentflow.core.api.TokenUsage;
import io.github.serfom256.intentflow.core.api.UserText;
import io.github.serfom256.intentflow.core.ast.DslSource;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import io.github.serfom256.intentflow.core.pipeline.LlmException;
import io.github.serfom256.intentflow.core.pipeline.LlmPrompts;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

public final class GeminiLlmClient implements LlmClient {

    private final Client client;
    private final String model;
    private final int maxTokens;
    private final Clock clock;

    public GeminiLlmClient(Client client, String model, int maxTokens, Clock clock) {
        this.client = client;
        this.model = model;
        this.maxTokens = maxTokens;
        this.clock = clock;
    }

    @Override
    public DslSource generateDsl(UserText normalizedText, ServiceCatalog catalog) {
        return complete(catalog, List.of(text("user", normalizedText.value())));
    }

    @Override
    public DslSource repairDsl(UserText normalizedText, ServiceCatalog catalog, DslSource failed, String error) {
        return complete(catalog, List.of(text("user", normalizedText.value()), text("model", failed.value()),
                text("user", LlmPrompts.repair(error))));
    }

    @Override
    public void ping() {
        try {
            client.models.get(model, null);
        } catch (ApiException e) {
            throw new LlmException("Gemini API check failed: " + e.getMessage(), e);
        } catch (GenAiIOException e) {
            throw new LlmException("Gemini API check failed: " + e.getMessage(), e);
        }
    }

    private DslSource complete(ServiceCatalog catalog, List<Content> contents) {
        GenerateContentConfig config = GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(LlmPrompts.system(catalog, LocalDate.now(clock)))))
                .temperature(0f).maxOutputTokens(maxTokens).build();
        GenerateContentResponse response;
        try {
            response = client.models.generateContent(model, contents, config);
        } catch (ApiException e) {
            throw new LlmException("Gemini request failed: " + e.getMessage(), e);
        } catch (GenAiIOException e) {
            throw new LlmException("Gemini request failed: " + e.getMessage(), e);
        }
        String text = response.text();
        String dsl = text == null ? "" : LlmPrompts.answer(text);
        if (dsl.isEmpty()) {
            throw new LlmException("LLM returned an empty answer");
        }
        return DslSources.of(dsl, usage(response), response.modelVersion().orElse(model));
    }

    private static TokenUsage usage(GenerateContentResponse response) {
        return response.usageMetadata().map(GeminiLlmClient::usage).orElse(TokenUsage.NONE);
    }

    private static TokenUsage usage(GenerateContentResponseUsageMetadata usage) {
        long completion = usage.candidatesTokenCount().orElse(0) + usage.thoughtsTokenCount().orElse(0);
        return new TokenUsage(usage.promptTokenCount().orElse(0), completion);
    }

    private static Content text(String role, String value) {
        return Content.builder().role(role).parts(Part.fromText(value)).build();
    }
}
