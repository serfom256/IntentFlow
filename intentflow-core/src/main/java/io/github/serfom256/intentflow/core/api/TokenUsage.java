package io.github.serfom256.intentflow.core.api;

public record TokenUsage(long promptTokens, long completionTokens) {

    public static final TokenUsage NONE = new TokenUsage(0, 0);

    public TokenUsage {
        if (promptTokens < 0 || completionTokens < 0) {
            throw new IllegalArgumentException("token counts must not be negative");
        }
    }

    public long total() {
        return promptTokens + completionTokens;
    }

    public TokenUsage plus(TokenUsage other) {
        return new TokenUsage(promptTokens + other.promptTokens, completionTokens + other.completionTokens);
    }
}
