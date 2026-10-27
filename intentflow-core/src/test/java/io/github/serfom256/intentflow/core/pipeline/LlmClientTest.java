package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.api.UserText;
import io.github.serfom256.intentflow.core.api.factories.UserTexts;
import io.github.serfom256.intentflow.core.ast.DslSource;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.pipeline.factories.LlmClients;
import io.github.serfom256.intentflow.core.pipeline.impl.StubLlmClient;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmClientTest {

    private static final UserText FIND_BOOKS = UserTexts.of("find all books");
    private static final DslSource BOOKS_DSL = DslSources.of("books | take 10");
    private static final ServiceCatalog EMPTY_CATALOG = emptyCatalog();

    @Test
    void stubReturnsRegisteredResponse() {
        LlmClient client = LlmClients.stub(Map.of(FIND_BOOKS, BOOKS_DSL));

        DslSource result = client.generateDsl(FIND_BOOKS, EMPTY_CATALOG);

        assertThat(result.value()).isEqualTo("books | take 10");
    }

    @Test
    void stubThrowsForUnregisteredInput() {
        LlmClient client = LlmClients.stub(Map.of());

        assertThatThrownBy(() -> client.generateDsl(FIND_BOOKS, EMPTY_CATALOG)).isInstanceOf(LlmException.class)
                .hasMessageContaining("find all books");
    }

    @Test
    void stubMatchesByUserTextEquality() {
        LlmClient client = LlmClients.stub(Map.of(FIND_BOOKS, BOOKS_DSL));

        UserText sameValue = UserTexts.of("find all books");
        DslSource result = client.generateDsl(sameValue, EMPTY_CATALOG);

        assertThat(result.value()).isEqualTo(BOOKS_DSL.value());
    }

    @Test
    void stubSupportsMultipleMappings() {
        UserText second = UserTexts.of("show authors");
        DslSource secondDsl = DslSources.of("authors | take 5");

        LlmClient client = LlmClients.stub(Map.of(FIND_BOOKS, BOOKS_DSL, second, secondDsl));

        assertThat(client.generateDsl(FIND_BOOKS, EMPTY_CATALOG).value()).isEqualTo("books | take 10");
        assertThat(client.generateDsl(second, EMPTY_CATALOG).value()).isEqualTo("authors | take 5");
    }

    @Test
    void stubRegisterAddsNewMapping() {
        LlmClient client = LlmClients.stub(Map.of(FIND_BOOKS, BOOKS_DSL));

        UserText extra = UserTexts.of("list all");
        DslSource extraDsl = DslSources.of("books");

        assertThatThrownBy(() -> client.generateDsl(extra, EMPTY_CATALOG)).isInstanceOf(LlmException.class);

        ((StubLlmClient) client).register(extra, extraDsl);

        assertThat(client.generateDsl(extra, EMPTY_CATALOG).value()).isEqualTo("books");
    }

    @Test
    void stubOverwritesExistingMapping() {
        LlmClient client = LlmClients.stub(Map.of(FIND_BOOKS, BOOKS_DSL));

        DslSource updated = DslSources.of("books | take 99");
        ((StubLlmClient) client).register(FIND_BOOKS, updated);

        assertThat(client.generateDsl(FIND_BOOKS, EMPTY_CATALOG).value()).isEqualTo("books | take 99");
    }

    @Test
    void stubIsConcurrentSafe() throws InterruptedException {
        int threads = 16;
        int opsPerThread = 500;
        LlmClient client = LlmClients.stub(Map.of(FIND_BOOKS, BOOKS_DSL));
        CountDownLatch latch = new CountDownLatch(threads);
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

        IntStream.range(0, threads).forEach(t -> Thread.startVirtualThread(() -> {
            try {
                for (int i = 0; i < opsPerThread; i++) {
                    DslSource r = client.generateDsl(FIND_BOOKS, EMPTY_CATALOG);
                    assertThat(r.value()).isEqualTo("books | take 10");
                }
            } catch (Throwable e) {
                errors.add(e);
            } finally {
                latch.countDown();
            }
        }));

        latch.await();
        assertThat(errors).isEmpty();
    }

    @Test
    void llmExceptionMessageIncludesInput() {
        LlmException ex = new LlmException("test message");
        assertThat(ex.getMessage()).isEqualTo("test message");
    }

    @Test
    void llmExceptionPreservesCause() {
        RuntimeException cause = new RuntimeException("root");
        LlmException ex = new LlmException("wrapped", cause);
        assertThat(ex.getCause()).isSameAs(cause);
    }

    private static ServiceCatalog emptyCatalog() {
        return new ServiceCatalog() {
            @Override
            public Optional<ServiceDescriptor> find(ServiceName name) {
                return Optional.empty();
            }

            @Override
            public Set<ServiceName> names() {
                return Set.of();
            }

            @Override
            public int version() {
                return 1;
            }
        };
    }
}
