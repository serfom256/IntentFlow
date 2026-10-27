package io.github.serfom256.intentflow.core.dsl;

import io.github.serfom256.intentflow.core.ast.AstQuery;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.dsl.factories.Lexers;
import io.github.serfom256.intentflow.core.dsl.factories.Parsers;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ParserConcurrencyTest {

    @Test
    void singleParserInstanceTolerates32ThreadsSharedTokens() throws Exception {
        int threads = 32;
        int iterations = 1000;

        Lexer lexer = Lexers.create();
        Parser parser = Parsers.create();

        List<Token> tokens = lexer.tokenize(
                DslSources.of("books | filter author == \"Doe\" and year >= 2000" + " | sort year desc | take 20"));

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger ok = new AtomicInteger();
        AtomicReference<Throwable> firstError = new AtomicReference<>();

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    for (int i = 0; i < iterations; i++) {
                        AstQuery q = parser.parse(tokens);
                        if (!q.service().value().equals("books")) {
                            throw new AssertionError("service mismatch: " + q.service().value());
                        }
                    }
                    ok.incrementAndGet();
                } catch (Throwable th) {
                    firstError.compareAndSet(null, th);
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        boolean finished = done.await(30, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(finished).as("all threads finished within timeout").isTrue();
        assertThat(firstError.get()).as("no thread threw").isNull();
        assertThat(ok.get()).isEqualTo(threads);
    }

    @Test
    void differentDslStringsAcrossThreadsAllSucceed() throws Exception {
        int threads = 16;
        Parser parser = Parsers.create();
        Lexer lexer = Lexers.create();

        String[] inputs = { "books", "books | filter year >= 2000", "books | filter title ~ \"ring\" | take 5",
                "books | sort year desc, title asc", "books | sort title | skip 10 | take 10",
                "books | filter (a == 1 or b == 2) and c == 3", "books | filter status in (\"open\", \"closed\")",
                "books | filter deletedAt == null", };

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger ok = new AtomicInteger();
        AtomicReference<Throwable> firstError = new AtomicReference<>();

        for (int t = 0; t < threads; t++) {
            int idx = t % inputs.length;
            pool.submit(() -> {
                try {
                    for (int i = 0; i < 500; i++) {
                        List<Token> toks = lexer.tokenize(DslSources.of(inputs[idx]));
                        AstQuery q = parser.parse(toks);
                        if (!q.service().value().equals("books")) {
                            throw new AssertionError("unexpected service");
                        }
                    }
                    ok.incrementAndGet();
                } catch (Throwable th) {
                    firstError.compareAndSet(null, th);
                } finally {
                    done.countDown();
                }
            });
        }

        boolean finished = done.await(30, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(finished).isTrue();
        assertThat(firstError.get()).isNull();
        assertThat(ok.get()).isEqualTo(threads);
    }
}
