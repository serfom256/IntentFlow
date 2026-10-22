package io.github.serfom256.intentflow.llm.bedrock;

import org.jspecify.annotations.Nullable;

public record BedrockSettings(@Nullable String region, int maxTokens, @Nullable Float temperature,
        boolean promptCaching) {

    public static BedrockSettings of(@Nullable String region) {
        return new BedrockSettings(region, 16000, null, false);
    }
}
