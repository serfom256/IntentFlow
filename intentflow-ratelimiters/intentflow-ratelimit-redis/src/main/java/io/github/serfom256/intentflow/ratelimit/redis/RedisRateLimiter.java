package io.github.serfom256.intentflow.ratelimit.redis;

import io.github.serfom256.intentflow.core.api.IntentContext;
import io.github.serfom256.intentflow.core.pipeline.LlmRateLimiter;
import io.github.serfom256.intentflow.core.pipeline.RateLimitExceededException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Function;

@Slf4j
public final class RedisRateLimiter implements LlmRateLimiter {

    private static final RedisScript<Long> TAKE = RedisScript.of("""
            local capacity = tonumber(ARGV[1])
            local period = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local state = redis.call('HMGET', KEYS[1], 'tokens', 'refilled')
            local tokens = tonumber(state[1]) or capacity
            local refilled = tonumber(state[2]) or now
            tokens = math.min(capacity, tokens + math.max(0, now - refilled) * capacity / period)
            if tokens < 1 then
                return math.ceil((1 - tokens) * period / capacity)
            end
            redis.call('HSET', KEYS[1], 'tokens', tostring(tokens - 1), 'refilled', tostring(now))
            redis.call('PEXPIRE', KEYS[1], period)
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final String prefix;
    private final int questions;
    private final Duration period;
    private final Function<IntentContext, String> key;
    private final boolean failOpen;
    private final Clock clock;

    public RedisRateLimiter(StringRedisTemplate redis, String prefix, int questions, Duration period,
            Function<IntentContext, String> key, boolean failOpen, Clock clock) {
        if (questions < 1) {
            throw new IllegalArgumentException("questions must be positive: " + questions);
        }
        if (period.toMillis() < 1) {
            throw new IllegalArgumentException("period must be at least 1ms: " + period);
        }
        this.redis = redis;
        this.prefix = prefix;
        this.questions = questions;
        this.period = period;
        this.key = key;
        this.failOpen = failOpen;
        this.clock = clock;
    }

    @Override
    public void acquire(IntentContext caller) {
        Long waitMillis;
        try {
            waitMillis = redis.execute(TAKE, List.of(prefix + hash(key.apply(caller))),
                    String.valueOf(questions), String.valueOf(period.toMillis()), String.valueOf(clock.millis()));
        } catch (RuntimeException e) {
            if (!failOpen) {
                throw new RateLimitExceededException("The rate limit store is unavailable", Duration.ofSeconds(5));
            }
            log.warn("Redis rate limit check failed, letting the question through: {}", e.toString());
            return;
        }
        if (waitMillis != null && waitMillis > 0) {
            throw new RateLimitExceededException("Rate limit of " + questions + " questions per " + period
                    + " reached", Duration.ofMillis(waitMillis));
        }
    }

    private static String hash(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
