package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.api.IntentRequest;
import io.github.serfom256.intentflow.core.api.IntentResponse;
import io.github.serfom256.intentflow.core.api.UserText;
import io.github.serfom256.intentflow.core.api.factories.IntentRequests;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.DslSource;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.catalog.ServiceNotFoundException;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import io.github.serfom256.intentflow.core.catalog.factories.ColumnNames;
import io.github.serfom256.intentflow.core.catalog.factories.FieldMappings;
import io.github.serfom256.intentflow.core.catalog.factories.LogicalFieldNames;
import io.github.serfom256.intentflow.core.catalog.factories.MaxTakes;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceDescriptors;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceNames;
import io.github.serfom256.intentflow.core.catalog.factories.SortKeys;
import io.github.serfom256.intentflow.core.catalog.factories.TableNames;
import io.github.serfom256.intentflow.core.catalog.factories.UriTemplates;
import io.github.serfom256.intentflow.core.pipeline.factories.DslCaches;
import io.github.serfom256.intentflow.core.pipeline.factories.IntentPipelines;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.core.testing.TestDialect;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntentPipelineTest {

    record Book(long id, String title) {
    }

    record Author(long id, String name) {
    }

    private static final ServiceName BOOKS = ServiceNames.of("books");
    private static final ServiceName AUTHORS = ServiceNames.of("authors");

    private static final ServiceDescriptor BOOKS_SERVICE = ServiceDescriptors.of(BOOKS, null, TableNames.of("book"),
            List.of(FieldMappings.of(LogicalFieldNames.of("id"), ColumnNames.of("id"), true),
                    FieldMappings.of(LogicalFieldNames.of("title"), ColumnNames.of("title"), true)),
            List.of(SortKeys.of(LogicalFieldNames.of("id"), SortKey.Direction.ASC)), MaxTakes.of(50),
            UriTemplates.of("/books/{{id}}"), Book.class);

    private static final ServiceDescriptor AUTHORS_SERVICE = ServiceDescriptors.of(AUTHORS, null,
            TableNames.of("author"), List.of(FieldMappings.of(LogicalFieldNames.of("id"), ColumnNames.of("id"), true)),
            List.of(), MaxTakes.of(50), UriTemplates.of("/authors/{{id}}"), Author.class);

    private static final ServiceCatalog CATALOG = ServiceCatalogs.of(BOOKS_SERVICE, AUTHORS_SERVICE);

    static final class RecordingExecutor implements QueryExecutor {
        final List<BoundQuery> queries = new ArrayList<>();
        final Map<Class<?>, List<?>> rows = new ConcurrentHashMap<>(Map.of(Book.class,
                List.of(new Book(42, "Dune"), new Book(7, "Emma")), Author.class, List.of(new Author(3, "Herbert"))));

        @Override
        public synchronized <T> List<T> execute(BoundQuery query, Class<T> resultType) {
            queries.add(query);
            return rows.getOrDefault(resultType, List.of()).stream().map(resultType::cast).toList();
        }
    }

    static final class ScriptedLlm implements LlmClient {
        final Map<String, String> script;
        final AtomicInteger calls = new AtomicInteger();
        final List<List<String>> shownServices = new ArrayList<>();

        ScriptedLlm(Map<String, String> script) {
            this.script = script;
        }

        @Override
        public synchronized DslSource generateDsl(UserText text, ServiceCatalog catalog) {
            calls.incrementAndGet();
            shownServices.add(catalog.names().stream().map(ServiceName::value).sorted().toList());
            String dsl = script.get(text.value());
            if (dsl == null) {
                throw new LlmException("no script for '" + text.value() + "'");
            }
            return DslSources.of(dsl);
        }
    }

    private final RecordingExecutor executor = new RecordingExecutor();

    private IntentPipeline pipeline(LlmClient llm) {
        return IntentPipelines.builder().llm(llm).sqlBuilder(SqlBuilders.create(new TestDialect())).executor(executor)
                .catalog(CATALOG).cache(DslCaches.create(Duration.ofMinutes(5), 100)).build();
    }

    @Test
    void untypedResolveReturnsEntitiesOfTheServiceTheLlmChose() {
        IntentPipeline pipe = pipeline(new ScriptedLlm(Map.of("who wrote dune", "authors | take 1")));

        IntentResponse<?> response = pipe.resolve(IntentRequests.of("who wrote dune"));

        assertThat(response.service()).isEqualTo(AUTHORS);
        assertThat(response.type()).isEqualTo(Author.class);
        List<?> items = response.items();
        assertThat(items).singleElement().isEqualTo(new Author(3, "Herbert"));
        assertThat(response.uri().value()).isEqualTo("/authors/3");
        assertThat(executor.queries.getFirst().sql()).isEqualTo("SELECT * FROM \"author\" LIMIT 1");
    }

    @Test
    void typedResolveReturnsTypedItems() {
        IntentPipeline pipe = pipeline(new ScriptedLlm(Map.of("dune", "books | filter title == \"Dune\"")));

        IntentResponse<Book> response = pipe.resolve(IntentRequests.of("dune"), Book.class);

        Book first = response.items().getFirst();
        assertThat(first.title()).isEqualTo("Dune");
        assertThat(response.type()).isEqualTo(Book.class);
        assertThat(response.uri().value()).isEqualTo("/books/42");
        assertThat(executor.queries.getFirst().values()).containsExactly("Dune");
    }

    @Test
    void typedResolveShowsTheLlmOnlyThatService() {
        ScriptedLlm llm = new ScriptedLlm(Map.of("everything", "books"));

        pipeline(llm).resolve(IntentRequests.of("everything"), Book.class);
        pipeline(llm).resolve(IntentRequests.of("everything"));

        assertThat(llm.shownServices).containsExactly(List.of("books"), List.of("authors", "books"));
    }

    @Test
    void typedResolveRejectsDslForAnotherService() {
        IntentPipeline pipe = pipeline(new ScriptedLlm(Map.of("sneaky", "authors")));

        assertThatThrownBy(() -> pipe.resolve(IntentRequests.of("sneaky"), Book.class))
                .isInstanceOf(ServiceNotFoundException.class).hasMessageContaining("authors");
        assertThat(executor.queries).isEmpty();
    }

    @Test
    void typedResolveForUnknownTypeFailsFast() {
        IntentPipeline pipe = pipeline(new ScriptedLlm(Map.of()));

        assertThatThrownBy(() -> pipe.resolve(IntentRequests.of("x"), String.class))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("java.lang.String");
    }

    @Test
    void emptyResultHasNoItemsAndACollectionUri() {
        executor.rows.put(Book.class, List.of());
        IntentPipeline pipe = pipeline(new ScriptedLlm(Map.of("nothing", "books | filter id < 0")));

        IntentResponse<Book> response = pipe.resolve(IntentRequests.of("nothing"), Book.class);

        assertThat(response.items()).isEmpty();
        assertThat(response.uri().value()).isEqualTo("/books");
    }

    @Test
    void normalizationCollapsesWhitespaceBeforeCacheLookup() {
        ScriptedLlm llm = new ScriptedLlm(Map.of("find all books", "books"));
        IntentPipeline pipe = pipeline(llm);

        pipe.resolve(IntentRequests.of("find all books"));
        pipe.resolve(IntentRequests.of("  find   all\tbooks  "));

        assertThat(llm.calls).hasValue(1);
    }

    @Test
    void cacheIsScopedPerResultType() {
        ScriptedLlm llm = new ScriptedLlm(Map.of("anything", "books"));
        IntentPipeline pipe = pipeline(llm);

        pipe.resolve(IntentRequests.of("anything"));
        pipe.resolve(IntentRequests.of("anything"), Book.class);
        pipe.resolve(IntentRequests.of("anything"), Book.class);
        pipe.resolve(IntentRequests.of("anything"));

        assertThat(llm.calls).hasValue(2);
    }

    @Test
    void differentInputsCallLlmSeparately() {
        ScriptedLlm llm = new ScriptedLlm(Map.of("a", "books", "b", "books | take 1"));
        IntentPipeline pipe = pipeline(llm);

        pipe.resolve(IntentRequests.of("a"));
        pipe.resolve(IntentRequests.of("b"));

        assertThat(llm.calls).hasValue(2);
    }

    @Test
    void propagatesLlmParseValidationAndUnsupportedFailures() {
        IntentPipeline pipe = pipeline(new ScriptedLlm(Map.of("garbage", "books |", "bad field",
                "books | filter ghost == 1", "declined", "unsupported \"books have no publisher field\"")));

        assertThatThrownBy(() -> pipe.resolve(IntentRequests.of("unscripted"))).isInstanceOf(LlmException.class);
        assertThatThrownBy(() -> pipe.resolve(IntentRequests.of("garbage"))).isInstanceOf(DslParseException.class);
        assertThatThrownBy(() -> pipe.resolve(IntentRequests.of("bad field"))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> pipe.resolve(IntentRequests.of("declined"))).isInstanceOfSatisfying(
                UnsupportedRequestException.class,
                e -> assertThat(e.reason()).isEqualTo("books have no publisher field"));
        assertThat(executor.queries).isEmpty();
    }

    @Test
    void executorReturningTheWrongTypeIsCaught() {
        QueryExecutor broken = new QueryExecutor() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
                return (List<T>) List.of("not a book");
            }
        };
        IntentPipeline pipe = IntentPipelines.builder().llm((text, catalog) -> DslSources.of("books"))
                .sqlBuilder(SqlBuilders.create(new TestDialect())).executor(broken).catalog(CATALOG).build();

        assertThatThrownBy(() -> pipe.resolve(IntentRequests.of("x"), Book.class))
                .hasMessageContaining("java.lang.String");
    }

    @Test
    void concurrentResolvesAreThreadSafe() throws InterruptedException {
        int threads = 16;
        int opsPerThread = 100;
        ScriptedLlm llm = new ScriptedLlm(Map.of("find all books", "books | take 10"));
        IntentPipeline pipe = pipeline(llm);

        IntentRequest req = IntentRequests.of("find all books");
        CountDownLatch latch = new CountDownLatch(threads);
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

        IntStream.range(0, threads).forEach(t -> Thread.startVirtualThread(() -> {
            try {
                for (int i = 0; i < opsPerThread; i++) {
                    IntentResponse<Book> r = pipe.resolve(req, Book.class);
                    assertThat(r.uri().value()).isEqualTo("/books/42");
                    assertThat(r.items()).hasSize(2);
                }
            } catch (Throwable e) {
                errors.add(e);
            } finally {
                latch.countDown();
            }
        }));

        latch.await();
        assertThat(errors).isEmpty();
        assertThat(executor.queries).hasSize(threads * opsPerThread);
    }
}
