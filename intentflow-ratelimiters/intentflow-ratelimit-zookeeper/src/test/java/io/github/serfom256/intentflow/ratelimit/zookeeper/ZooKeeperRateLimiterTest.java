package io.github.serfom256.intentflow.ratelimit.zookeeper;

import io.github.serfom256.intentflow.core.api.IntentContext;
import io.github.serfom256.intentflow.core.api.factories.IntentContexts;
import io.github.serfom256.intentflow.core.pipeline.RateLimitExceededException;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryNTimes;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZooKeeperRateLimiterTest {

    private static final IntentContext ANN = IntentContexts.of("ann", "acme", Set.of());
    private static final IntentContext BOB = IntentContexts.of("bob", "acme", Set.of());

    private final MovableClock clock = new MovableClock();
    private final List<CuratorFramework> clients = new ArrayList<>();
    private TestingServer server;

    private static final class MovableClock extends Clock {

        private volatile Instant now = Instant.parse("2026-10-22T12:00:00Z");

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

    @BeforeEach
    void start() throws Exception {
        server = new TestingServer(true);
    }

    @AfterEach
    void stop() throws Exception {
        clients.forEach(CuratorFramework::close);
        server.close();
    }

    private ZooKeeperRateLimiter instance(int questions, boolean failOpen) {
        CuratorFramework client = CuratorFrameworkFactory.builder().connectString(server.getConnectString())
                .retryPolicy(new RetryNTimes(1, 50)).connectionTimeoutMs(1000).sessionTimeoutMs(3000).build();
        client.start();
        clients.add(client);
        return new ZooKeeperRateLimiter(client, "/intentflow/rate-limit", questions, Duration.ofMinutes(1),
                caller -> String.valueOf(caller.principal()), failOpen, clock);
    }

    @Test
    void instancesShareOneLimitPerCaller() {
        ZooKeeperRateLimiter first = instance(3, true);
        ZooKeeperRateLimiter second = instance(3, true);

        first.acquire(ANN);
        second.acquire(ANN);
        first.acquire(ANN);

        assertThatThrownBy(() -> second.acquire(ANN)).isInstanceOfSatisfying(RateLimitExceededException.class,
                e -> assertThat(e.retryAfter()).isEqualTo(Duration.ofSeconds(20)));
        second.acquire(BOB);
        clock.advance(Duration.ofSeconds(20));
        second.acquire(ANN);
    }

    @Test
    void concurrentQuestionsNeverExceedTheLimit() throws Exception {
        List<ZooKeeperRateLimiter> instances = List.of(instance(5, true), instance(5, true));
        List<Callable<Boolean>> questions = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            ZooKeeperRateLimiter limiter = instances.get(i % 2);
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

    @Test
    void callerNamesAreNotStoredInClearText() throws Exception {
        instance(3, true).acquire(ANN);

        List<String> nodes = clients.getFirst().getChildren().forPath("/intentflow/rate-limit");
        assertThat(nodes).singleElement().asString().hasSize(64).doesNotContain("ann");
    }

    @Test
    void anUnreachableStoreLetsQuestionsThroughUnlessFailClosed() throws Exception {
        ZooKeeperRateLimiter open = instance(1, true);
        ZooKeeperRateLimiter closed = instance(1, false);
        server.stop();

        open.acquire(ANN);
        open.acquire(ANN);
        assertThatThrownBy(() -> closed.acquire(ANN)).isInstanceOf(RateLimitExceededException.class)
                .hasMessage("The rate limit store is unavailable");
    }
}
