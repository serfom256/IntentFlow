package io.github.serfom256.intentflow.core.pipeline.factories;

import io.github.serfom256.intentflow.core.api.IntentContext;
import io.github.serfom256.intentflow.core.pipeline.LlmRateLimiter;
import io.github.serfom256.intentflow.core.pipeline.impl.TokenBucketRateLimiter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Clock;
import java.time.Duration;
import java.util.function.Function;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LlmRateLimiters {

    private static final String ANONYMOUS = "anonymous";

    public static LlmRateLimiter perPrincipal(int questions, Duration period) {
        return of(questions, period, byPrincipal(), Clock.systemUTC());
    }

    public static LlmRateLimiter perTenant(int questions, Duration period) {
        return of(questions, period, byTenant(), Clock.systemUTC());
    }

    public static Function<IntentContext, String> byPrincipal() {
        return LlmRateLimiters::principal;
    }

    public static Function<IntentContext, String> byTenant() {
        return caller -> caller.tenant() != null ? caller.tenant() : principal(caller);
    }

    public static LlmRateLimiter of(int questions, Duration period, Function<IntentContext, String> key,
            Clock clock) {
        return new TokenBucketRateLimiter(questions, period, key, clock);
    }

    private static String principal(IntentContext caller) {
        return caller.principal() != null ? caller.principal() : ANONYMOUS;
    }
}
