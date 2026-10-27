package io.github.serfom256.intentflow.ratelimit.redis;

import io.github.serfom256.intentflow.core.api.IntentContext;
import io.github.serfom256.intentflow.core.api.factories.IntentContexts;
import io.github.serfom256.intentflow.core.pipeline.RateLimitExceededException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisRateLimiterTest {

    private static final IntentContext ANN = IntentContexts.of("ann", "acme", Set.of());
    private static final IntentContext BOB = IntentContexts.of("bob", "acme", Set.of());
    private static final Map<String, GenericContainer<?>> SERVERS = new ConcurrentHashMap<>();
    private static final List<LettuceConnectionFactory> FACTORIES = new ArrayList<>();

    private final MovableClock clock = new MovableClock();

    private static final class MovableClock extends Clock {

        private volatile Instant now = Instant.parse("2026-10-24T12:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @AfterAll
    static void stop() {
        FACTORIES.forEach(LettuceConnectionFactory::destroy);
        SERVERS.values().forEach(GenericContainer::stop);
    }

    private static StringRedisTemplate redis(String host, int port) {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port));
        factory.afterPropertiesSet();
        FACTORIES.add(factory);
        return new StringRedisTemplate(factory);
    }

    private static StringRedisTemplate redis(String image) {
        GenericContainer<?> server = SERVERS.computeIfAbsent(image, i -> {
            GenericContainer<?> container = new GenericContainer<>(i).withExposedPorts(6379);
            container.start();
            return container;
        });
        StringRedisTemplate template = redis(server.getHost(), server.getMappedPort(6379));
        template.execute((RedisCallback<Object>) c -> {
            c.serverCommands().flushAll();
            return null;
        });
        return template;
    }

    private RedisRateLimiter limiter(StringRedisTemplate redis, int questions, boolean failOpen) {
        return new RedisRateLimiter(redis, "intentflow:rate-limit:", questions, Duration.ofMinutes(1),
                caller -> caller.principal().orElse("anonymous"), failOpen, clock);
    }

    @ParameterizedTest
    @ValueSource(strings = { "redis:8.8.2-alpine", "valkey/valkey:9.1.2-alpine" })
    void instancesShareOneLimitPerCaller(String image) {
        StringRedisTemplate redis = redis(image);
        RedisRateLimiter first = limiter(redis, 3, true);
        RedisRateLimiter second = limiter(redis(image), 3, true);

        first.acquire(ANN);
        second.acquire(ANN);
        first.acquire(ANN);

        assertThatThrownBy(() -> second.acquire(ANN)).isInstanceOfSatisfying(RateLimitExceededException.class,
                e -> assertThat(e.retryAfter()).isEqualTo(Duration.ofSeconds(20)));
        second.acquire(BOB);
        clock.advance(Duration.ofSeconds(20));
        second.acquire(ANN);
    }

    @ParameterizedTest
    @ValueSource(strings = { "redis:8.8.2-alpine", "valkey/valkey:9.1.2-alpine" })
    void concurrentQuestionsNeverExceedTheLimit(String image) throws Exception {
        StringRedisTemplate redis = redis(image);
        List<RedisRateLimiter> instances = List.of(limiter(redis, 5, true), limiter(redis(image), 5, true));
        List<Callable<Boolean>> questions = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            RedisRateLimiter limiter = instances.get(i % 2);
            questions.add(() -> {
                try {
                    limiter.acquire(ANN);
                    return true;
                } catch (RateLimitExceededException e) {
                    return false;
                }
            });
        }
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            long allowed = 0;
            for (Future<Boolean> answer : pool.invokeAll(questions)) {
                allowed += answer.get() ? 1 : 0;
            }
            assertThat(allowed).isEqualTo(5);
        } finally {
            pool.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "redis:8.8.2-alpine", "valkey/valkey:9.1.2-alpine" })
    void bucketsAreHashedAndExpireAfterAPeriod(String image) {
        StringRedisTemplate redis = redis(image);
        limiter(redis, 3, true).acquire(ANN);

        Set<String> keys = redis.keys("intentflow:rate-limit:*");
        assertThat(keys).singleElement().asString().hasSize("intentflow:rate-limit:".length() + 64)
                .doesNotContain("ann");
        assertThat(redis.getExpire(keys.iterator().next())).isBetween(1L, 60L);
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void anUnreachableStoreLetsQuestionsThroughUnlessFailClosed(boolean failOpen) {
        RedisRateLimiter limiter = limiter(redis("localhost", 1), 1, failOpen);

        if (failOpen) {
            limiter.acquire(ANN);
            limiter.acquire(ANN);
        } else {
            assertThatThrownBy(() -> limiter.acquire(ANN)).isInstanceOf(RateLimitExceededException.class)
                    .hasMessage("The rate limit store is unavailable");
        }
    }
}
