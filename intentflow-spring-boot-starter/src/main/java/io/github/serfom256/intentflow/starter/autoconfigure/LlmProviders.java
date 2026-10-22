package io.github.serfom256.intentflow.starter.autoconfigure;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.ClassUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class LlmProviders {

    static final String OPENAI = "openai";

    private static final Map<String, String> MARKERS = new LinkedHashMap<>();

    static {
        MARKERS.put(OPENAI, "io.github.serfom256.intentflow.llm.openai.OpenAiCompatibleLlmClient");
        MARKERS.put("azure-openai", "io.github.serfom256.intentflow.llm.azure.AzureOpenAiLlmClients");
        MARKERS.put("gigachat", "io.github.serfom256.intentflow.llm.gigachat.GigaChatLlmClients");
        MARKERS.put("anthropic", "io.github.serfom256.intentflow.llm.anthropic.AnthropicLlmClients");
        MARKERS.put("vertex", "io.github.serfom256.intentflow.llm.vertex.GeminiLlmClients");
        MARKERS.put("bedrock", "io.github.serfom256.intentflow.llm.bedrock.BedrockLlmClients");
    }

    private LlmProviders() {
    }

    static List<String> present(@Nullable ClassLoader classLoader) {
        List<String> present = MARKERS.entrySet().stream()
                .filter(marker -> ClassUtils.isPresent(marker.getValue(), classLoader)).map(Map.Entry::getKey)
                .toList();
        return present.size() > 1 ? present.stream().filter(name -> !name.equals(OPENAI)).toList() : present;
    }

    static final class Selected implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Map<String, @Nullable Object> attributes = metadata
                    .getAnnotationAttributes(ConditionalOnLlmProvider.class.getName());
            String provider = attributes == null ? null : (String) attributes.get("value");
            if (provider == null || !ClassUtils.isPresent(MARKERS.get(provider), context.getClassLoader())) {
                return false;
            }
            String chosen = context.getEnvironment().getProperty("intentflow.llm.provider");
            if (chosen != null && !chosen.isBlank()) {
                return chosen.strip().equalsIgnoreCase(provider);
            }
            return present(context.getClassLoader()).equals(List.of(provider));
        }
    }
}
