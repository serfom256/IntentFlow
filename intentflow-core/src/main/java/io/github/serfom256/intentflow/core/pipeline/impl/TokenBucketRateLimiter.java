package io.github.serfom256.intentflow.core.pipeline.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.serfom256.intentflow.core.api.IntentContext;
import io.github.serfom256.intentflow.core.pipeline.LlmRateLimiter;
import io.github.serfom256.intentflow.core.pipeline.RateLimitExceededException;

import java.time.Clock;
import java.time.Duration;
import java.util.function.Function;

public final class TokenBucketRateLimiter implements LlmRateLimiter {

    private final int questions;
    private final Duration period;
    private final Function<IntentContext, String> key;
    private final Clock clock;
    private final Cache<String, Bucket> buckets;

    public TokenBucketRateLimiter(int questions, Duration period, Function<IntentContext, String> key, Clock clock) {
        if (questions < 1) {
            throw new IllegalArgumentException("questions must be positive: " + questions);
        }
        if (period.toMillis() < 1) {
            throw new IllegalArgumentException("period must be at least 1ms: " + period);
        }
        this.questions = questions;
        this.period = period;
        this.key = key;
        this.clock = clock;
        this.buckets = Caffeine.newBuilder().expireAfterAccess(period).maximumSize(100_000).build();
    }

    @Override
    public void acquire(IntentContext caller) {
        Bucket bucket = buckets.get(key.apply(caller), k -> new Bucket(questions, clock.millis()));
        long waitMillis = bucket.take(clock.millis());
        if (waitMillis > 0) {
            throw new RateLimitExceededException("Rate limit of " + questions + " questions per " + period
                    + " reached", Duration.ofMillis(waitMillis));
        }
    }

    private final class Bucket {

        private double tokens;
        private long refilledAt;

        Bucket(double tokens, long now) {
            this.tokens = tokens;
            this.refilledAt = now;
        }

        synchronized long take(long now) {
            double perMilli = (double) questions / period.toMillis();
            tokens = Math.min(questions, tokens + Math.max(0, now - refilledAt) * perMilli);
            refilledAt = now;
            if (tokens >= 1) {
                tokens -= 1;
                return 0;
            }
            return (long) Math.ceil((1 - tokens) / perMilli);
        }
    }
}
