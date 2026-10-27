package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.api.IntentContext;
import io.github.serfom256.intentflow.core.api.factories.IntentContexts;
import io.github.serfom256.intentflow.core.api.factories.IntentRequests;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.pipeline.PipelineListener.Outcome;
import io.github.serfom256.intentflow.core.pipeline.factories.IntentPipelines;
import io.github.serfom256.intentflow.core.pipeline.factories.LlmRateLimiters;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.core.testing.TestDialect;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitTest {

    @IntentService(value = "books", uri = "/books/{{id}}", defaultSort = "id")
    record Book(@IntentField long id, @IntentField String title) {
    }

    private static final ServiceCatalog CATALOG = ServiceCatalogs.fromAnnotated(Book.class);
    private static final IntentContext ANN = IntentContexts.of("ann", "acme", Set.of());
    private static final IntentContext BOB = IntentContexts.of("bob", "acme", Set.of());
    private static final IntentContext EVE = IntentContexts.of("eve", "globex", Set.of());

    private final MovableClock clock = new MovableClock();
    private final AtomicInteger llmCalls = new AtomicInteger();
    private final List<Outcome> outcomes = new ArrayList<>();

    private static final class MovableClock extends Clock {

        private Instant now = Instant.parse("2026-10-21T12:00:00Z");

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

    private LlmRateLimiter perPrincipal(int questions, Duration period) {
        return LlmRateLimiters.of(questions, period, caller -> caller.principal().orElse("anonymous"), clock);
    }

    private IntentPipeline pipeline(LlmRateLimiter limiter) {
        PipelineListener listener = new PipelineListener() {
            @Override
            public void request(@Nullable ServiceName service, Outcome outcome, Duration elapsed) {
                outcomes.add(outcome);
            }
        };
        return IntentPipelines.builder().llm((text, catalog) -> {
            llmCalls.incrementAndGet();
            return DslSources.of("books");
        }).sqlBuilder(SqlBuilders.create(new TestDialect())).executor(new QueryExecutor() {
            @Override
            public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
                return List.of();
            }
        }).catalog(CATALOG).listener(listener).rateLimiter(limiter).build();
    }

    @Test
    void theBucketRefillsOverThePeriod() {
        LlmRateLimiter limiter = perPrincipal(2, Duration.ofMinutes(1));
        limiter.acquire(ANN);
        limiter.acquire(ANN);

        assertThatThrownBy(() -> limiter.acquire(ANN))
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        e -> assertThat(e.retryAfter()).isEqualTo(Duration.ofSeconds(30)))
                .hasMessage("Rate limit of 2 questions per PT1M reached");
        clock.advance(Duration.ofSeconds(30));
        limiter.acquire(ANN);
        assertThatThrownBy(() -> limiter.acquire(ANN)).isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void eachPrincipalHasItsOwnBucket() {
        LlmRateLimiter limiter = LlmRateLimiters.perPrincipal(1, Duration.ofMinutes(1));
        limiter.acquire(ANN);

        limiter.acquire(BOB);
        assertThatThrownBy(() -> limiter.acquire(ANN)).isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void aTenantSharesOneBucket() {
        LlmRateLimiter limiter = LlmRateLimiters.perTenant(1, Duration.ofMinutes(1));
        limiter.acquire(ANN);

        assertThatThrownBy(() -> limiter.acquire(BOB)).isInstanceOf(RateLimitExceededException.class);
        limiter.acquire(EVE);
    }

    @Test
    void anonymousCallersShareABucket() {
        LlmRateLimiter limiter = LlmRateLimiters.perPrincipal(1, Duration.ofMinutes(1));
        limiter.acquire(IntentContexts.anonymous());

        assertThatThrownBy(() -> limiter.acquire(IntentContexts.anonymous()))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void onlyQuestionsThatNeedTheLlmAreCounted() {
        IntentPipeline pipeline = pipeline(perPrincipal(1, Duration.ofMinutes(1)));

        pipeline.resolve(IntentRequests.of("all books", ANN));
        pipeline.resolve(IntentRequests.of("all books", ANN));
        pipeline.resolve(IntentRequests.of("all books", ANN));

        assertThatThrownBy(() -> pipeline.resolve(IntentRequests.of("every book", ANN)))
                .isInstanceOf(RateLimitExceededException.class);
        assertThat(llmCalls).hasValue(1);
        assertThat(outcomes).containsExactly(Outcome.ANSWERED, Outcome.ANSWERED, Outcome.ANSWERED, Outcome.LIMITED);
    }
}
