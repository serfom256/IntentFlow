package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.api.UserText;
import io.github.serfom256.intentflow.core.api.factories.UserTexts;
import io.github.serfom256.intentflow.core.ast.DslSource;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.pipeline.factories.LlmClients;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmResilienceTest {

    private static final UserText QUESTION = UserTexts.of("books");
    private static final ServiceCatalog CATALOG = ServiceCatalogs.of(List.of());

    private static final class ScriptedLlm implements LlmClient {

        private final Deque<RuntimeException> failures = new ArrayDeque<>();
        private final AtomicInteger calls = new AtomicInteger();
        private final String answer;

        ScriptedLlm(String answer) {
            this.answer = answer;
        }

        ScriptedLlm failing(RuntimeException... next) {
            failures.addAll(List.of(next));
            return this;
        }

        @Override
        public DslSource generateDsl(UserText normalizedText, ServiceCatalog catalog) {
            calls.incrementAndGet();
            RuntimeException failure = failures.poll();
            if (failure != null) {
                throw failure;
            }
            return DslSources.of(answer);
        }
    }

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

    private static String ask(LlmClient llm) {
        return llm.generateDsl(QUESTION, CATALOG).value();
    }

    private static LlmException down() {
        return new LlmException("LLM returned HTTP 503");
    }

    @Nested
    class ConcurrencyLimit {

        @Test
        void callsBeyondTheLimitFailFastAsUnavailable() throws Exception {
            CountDownLatch inside = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            LlmClient slow = (text, catalog) -> {
                inside.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return DslSources.of("books");
            };
            LlmClient limited = LlmClients.limited(slow, 1, Duration.ZERO);

            CompletableFuture<String> first = CompletableFuture.supplyAsync(() -> ask(limited));
            inside.await(5, TimeUnit.SECONDS);

            assertThatThrownBy(() -> ask(limited)).isInstanceOf(LlmUnavailableException.class)
                    .hasMessage("LLM is busy: the limit of 1 concurrent calls is reached");
            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("books");
            assertThat(ask(limited)).isEqualTo("books");
        }

        @Test
        void aFailedCallGivesItsPermitBack() {
            ScriptedLlm llm = new ScriptedLlm("books").failing(down());
            LlmClient limited = LlmClients.limited(llm, 1, Duration.ZERO);

            assertThatThrownBy(() -> ask(limited)).isInstanceOf(LlmException.class);
            assertThat(ask(limited)).isEqualTo("books");
        }
    }

    @Nested
    class CircuitBreaker {

        private final MovableClock clock = new MovableClock();

        @Test
        void opensAfterTheThresholdAndFailsFastUntilItIsTimeToTryAgain() {
            ScriptedLlm llm = new ScriptedLlm("books").failing(down(), down());
            LlmClient breaker = LlmClients.circuitBreaker(llm, 2, Duration.ofSeconds(30), clock);

            assertThatThrownBy(() -> ask(breaker)).hasMessage("LLM returned HTTP 503");
            assertThatThrownBy(() -> ask(breaker)).hasMessage("LLM returned HTTP 503");
            clock.advance(Duration.ofSeconds(10));

            assertThatThrownBy(() -> ask(breaker)).isInstanceOfSatisfying(LlmUnavailableException.class,
                    e -> assertThat(e.retryAfter()).contains(Duration.ofSeconds(20)))
                    .hasMessage("LLM circuit is open after repeated failures (threshold 2)");
            assertThat(llm.calls).hasValue(2);
            clock.advance(Duration.ofSeconds(20));
            assertThat(ask(breaker)).isEqualTo("books");
            assertThat(ask(breaker)).isEqualTo("books");
        }

        @Test
        void aFailedTrialOpensTheCircuitAgain() {
            ScriptedLlm llm = new ScriptedLlm("books").failing(down(), down());
            LlmClient breaker = LlmClients.circuitBreaker(llm, 1, Duration.ofSeconds(30), clock);
            assertThatThrownBy(() -> ask(breaker)).isNotInstanceOf(LlmUnavailableException.class);
            clock.advance(Duration.ofSeconds(30));

            assertThatThrownBy(() -> ask(breaker)).hasMessage("LLM returned HTTP 503");

            assertThatThrownBy(() -> ask(breaker)).isInstanceOf(LlmUnavailableException.class);
            assertThat(llm.calls).hasValue(2);
        }

        @Test
        void aSuccessResetsTheCount() {
            ScriptedLlm llm = new ScriptedLlm("books").failing(down());
            LlmClient breaker = LlmClients.circuitBreaker(llm, 2, Duration.ofSeconds(30), clock);
            assertThatThrownBy(() -> ask(breaker)).isInstanceOf(LlmException.class);
            ask(breaker);
            llm.failing(down());

            assertThatThrownBy(() -> ask(breaker)).hasMessage("LLM returned HTTP 503");
            assertThat(ask(breaker)).isEqualTo("books");
        }

        @Test
        void busyAndProgrammingErrorsDoNotCount() {
            ScriptedLlm llm = new ScriptedLlm("books").failing(new LlmUnavailableException("busy", null),
                    new IllegalStateException("bug"));
            LlmClient breaker = LlmClients.circuitBreaker(llm, 1, Duration.ofSeconds(30), clock);

            assertThatThrownBy(() -> ask(breaker)).hasMessage("busy");
            assertThatThrownBy(() -> ask(breaker)).hasMessage("bug");
            assertThat(ask(breaker)).isEqualTo("books");
        }
    }

    @Nested
    class Fallback {

        @Test
        void theFallbackAnswersWhenThePrimaryFails() {
            ScriptedLlm primary = new ScriptedLlm("primary").failing(down());
            LlmClient llm = LlmClients.withFallback(primary, new ScriptedLlm("fallback"));

            assertThat(ask(llm)).isEqualTo("fallback");
            assertThat(ask(llm)).isEqualTo("primary");
        }

        @Test
        void whenBothFailTheFallbacksErrorKeepsThePrimarys() {
            LlmException primaryFailure = down();
            LlmException fallbackFailure = new LlmException("fallback is down too");
            LlmClient llm = LlmClients.withFallback(new ScriptedLlm("primary").failing(primaryFailure),
                    new ScriptedLlm("fallback").failing(fallbackFailure));

            assertThatThrownBy(() -> ask(llm)).isSameAs(fallbackFailure);
            assertThat(fallbackFailure.getSuppressed()).containsExactly(primaryFailure);
        }

        @Test
        void programmingErrorsAreNotRetriedOnTheFallback() {
            ScriptedLlm fallback = new ScriptedLlm("fallback");
            LlmClient llm = LlmClients.withFallback(new ScriptedLlm("primary").failing(new IllegalStateException(
                    "bug")), fallback);

            assertThatThrownBy(() -> ask(llm)).hasMessage("bug");
            assertThat(fallback.calls).hasValue(0);
        }

        @Test
        void anOpenCircuitSendsQuestionsToTheFallbackWithoutCallingThePrimary() {
            ScriptedLlm primary = new ScriptedLlm("primary").failing(down());
            LlmClient llm = LlmClients.withFallback(
                    LlmClients.circuitBreaker(primary, 1, Duration.ofSeconds(30)), new ScriptedLlm("fallback"));

            assertThat(ask(llm)).isEqualTo("fallback");
            assertThat(ask(llm)).isEqualTo("fallback");
            assertThat(primary.calls).hasValue(1);
        }

        @Test
        void healthChecksThePrimary() {
            AtomicInteger pings = new AtomicInteger();
            LlmClient primary = new LlmClient() {
                @Override
                public DslSource generateDsl(UserText normalizedText, ServiceCatalog catalog) {
                    return DslSources.of("primary");
                }

                @Override
                public void ping() {
                    pings.incrementAndGet();
                }
            };

            LlmClients.withFallback(LlmClients.circuitBreaker(LlmClients.limited(primary, 1, Duration.ZERO), 1,
                    Duration.ofSeconds(1)), new ScriptedLlm("fallback")).ping();

            assertThat(pings).hasValue(1);
        }
    }
}
