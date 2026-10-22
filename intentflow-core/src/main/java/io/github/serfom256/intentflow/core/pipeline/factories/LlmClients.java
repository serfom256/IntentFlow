package io.github.serfom256.intentflow.core.pipeline.factories;

import io.github.serfom256.intentflow.core.api.UserText;
import io.github.serfom256.intentflow.core.ast.DslSource;
import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import io.github.serfom256.intentflow.core.pipeline.impl.CircuitBreakerLlmClient;
import io.github.serfom256.intentflow.core.pipeline.impl.ConcurrencyLimitedLlmClient;
import io.github.serfom256.intentflow.core.pipeline.impl.FallbackLlmClient;
import io.github.serfom256.intentflow.core.pipeline.impl.StubLlmClient;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LlmClients {

    public static LlmClient stub(Map<UserText, DslSource> responses) {
        return new StubLlmClient(responses);
    }

    public static LlmClient limited(LlmClient client, int maxCalls, Duration maxWait) {
        return new ConcurrencyLimitedLlmClient(client, maxCalls, maxWait);
    }

    public static LlmClient circuitBreaker(LlmClient client, int failureThreshold, Duration openFor) {
        return circuitBreaker(client, failureThreshold, openFor, Clock.systemUTC());
    }

    public static LlmClient circuitBreaker(LlmClient client, int failureThreshold, Duration openFor, Clock clock) {
        return new CircuitBreakerLlmClient(client, failureThreshold, openFor, clock);
    }

    public static LlmClient withFallback(LlmClient primary, LlmClient fallback) {
        return new FallbackLlmClient(primary, fallback);
    }
}
