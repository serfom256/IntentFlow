package io.github.serfom256.intentflow.llm.anthropic;

import org.jspecify.annotations.Nullable;

public record AnthropicSettings(long maxTokens, @Nullable String effort, boolean refusalFallback) {

    public static AnthropicSettings defaults() {
        return new AnthropicSettings(16000, null, true);
    }
}
