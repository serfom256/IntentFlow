package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.api.UserText;
import io.github.serfom256.intentflow.core.api.factories.UserTexts;
import io.github.serfom256.intentflow.core.ast.DslSource;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceNames;
import io.github.serfom256.intentflow.core.pipeline.factories.DslCaches;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DslCacheTest {

    private static DslCacheKey key(String text) {
        return new DslCacheKey(UserTexts.of(text), null);
    }

    @Test
    void sameTextWithDifferentScopesAreDifferentEntries() {
        DslCache cache = DslCaches.create(Duration.ofMinutes(1), 10);
        cache.put(key("dune"), DslSources.of("books"));
        cache.put(new DslCacheKey(UserTexts.of("dune"), ServiceNames.of("books")), DslSources.of("books | take 1"));

        assertThat(cache.get(key("dune")).orElseThrow().value()).isEqualTo("books");
        assertThat(cache.get(new DslCacheKey(UserTexts.of("dune"), ServiceNames.of("books"))).orElseThrow().value())
                .isEqualTo("books | take 1");
        assertThat(cache.get(new DslCacheKey(UserTexts.of("dune"), ServiceNames.of("authors")))).isEmpty();
    }

    private final DslCache cache = DslCaches.create();

    @Test
    void getOnEmptyCacheReturnsEmpty() {
        assertThat(cache.get(key("nothing"))).isEmpty();
    }

    @Test
    void putThenGetReturnsValue() {
        DslCacheKey key = key("find books by Doe");
        DslSource dsl = DslSources.of("books | filter author == \"Doe\"");
        cache.put(key, dsl);
        assertThat(cache.get(key)).contains(dsl);
    }

    @Test
    void equalKeysHitEvenWhenDifferentInstances() {
        cache.put(key("same input"), DslSources.of("books"));
        assertThat(cache.get(key("same input"))).isPresent();
    }

    @Test
    void differentKeysAreIndependent() {
        cache.put(key("a"), DslSources.of("books"));
        cache.put(key("b"), DslSources.of("authors"));
        assertThat(cache.get(key("a")).orElseThrow().value()).isEqualTo("books");
        assertThat(cache.get(key("b")).orElseThrow().value()).isEqualTo("authors");
    }

    @Test
    void invalidateRemovesSingleEntry() {
        DslCacheKey a = key("a");
        DslCacheKey b = key("b");
        cache.put(a, DslSources.of("foo"));
        cache.put(b, DslSources.of("bar"));
        cache.invalidate(a);
        assertThat(cache.get(a)).isEmpty();
        assertThat(cache.get(b)).isPresent();
    }

    @Test
    void invalidateAllClearsEverything() {
        cache.put(key("a"), DslSources.of("foo"));
        cache.put(key("b"), DslSources.of("bar"));
        cache.invalidateAll();
        assertThat(cache.get(key("a"))).isEmpty();
        assertThat(cache.get(key("b"))).isEmpty();
    }

    @Test
    void putOverwritesExistingEntry() {
        DslCacheKey k = key("k");
        cache.put(k, DslSources.of("one"));
        cache.put(k, DslSources.of("two"));
        assertThat(cache.get(k).orElseThrow().value()).isEqualTo("two");
    }

    @Test
    void entriesExpireAfterTtl() throws InterruptedException {
        DslCache shortLived = DslCaches.create(Duration.ofMillis(100), 100);
        DslCacheKey k = key("ephemeral");
        shortLived.put(k, DslSources.of("books"));
        assertThat(shortLived.get(k)).isPresent();
        Thread.sleep(250);
        assertThat(shortLived.get(k)).isEmpty();
    }

    @Test
    void factoryRejectsZeroTtl() {
        assertThatThrownBy(() -> DslCaches.create(Duration.ZERO, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ttl");
    }

    @Test
    void factoryRejectsNegativeTtl() {
        assertThatThrownBy(() -> DslCaches.create(Duration.ofSeconds(-1), 100))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void factoryRejectsNonPositiveMaxSize() {
        assertThatThrownBy(() -> DslCaches.create(Duration.ofMinutes(1), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxSize");
        assertThatThrownBy(() -> DslCaches.create(Duration.ofMinutes(1), -5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cacheToleratesConcurrentMixedOperations() throws Exception {
        DslCache c = DslCaches.create();
        int threads = 16;
        int ops = 500;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicReference<Throwable> err = new AtomicReference<>();

        for (int t = 0; t < threads; t++) {
            int seed = t;
            pool.submit(() -> {
                try {
                    for (int i = 0; i < ops; i++) {
                        DslCacheKey k = key("key-" + (i % 20));
                        switch (i % 4) {
                            case 0 -> c.put(k, DslSources.of("dsl-" + seed));
                            case 1 -> c.get(k);
                            case 2 -> c.invalidate(k);
                            case 3 -> {
                                if (i % 100 == 0)
                                    c.invalidateAll();
                                else
                                    c.get(k);
                            }
                        }
                    }
                } catch (Throwable th) {
                    err.compareAndSet(null, th);
                } finally {
                    done.countDown();
                }
            });
        }

        boolean finished = done.await(15, TimeUnit.SECONDS);
        pool.shutdownNow();
        assertThat(finished).isTrue();
        assertThat(err.get()).isNull();
    }
}
