package io.github.serfom256.intentflow.reactive;

import io.github.serfom256.intentflow.core.ErrorCode;
import io.github.serfom256.intentflow.core.IntentFlowException;
import io.github.serfom256.intentflow.core.api.IntentContext;
import io.github.serfom256.intentflow.core.api.IntentResponse;
import io.github.serfom256.intentflow.core.api.factories.IntentContexts;
import io.github.serfom256.intentflow.core.api.factories.IntentRequests;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.pipeline.AccessPolicy;
import io.github.serfom256.intentflow.core.pipeline.IntentPipeline;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import io.github.serfom256.intentflow.core.pipeline.factories.IntentPipelines;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.dialect.postgresql.PostgresDialect;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReactiveIntentPipelineTest {

    @IntentService(value = "books", uri = "/books/{{id}}", defaultSort = "id")
    record Book(@IntentField long id, @IntentField String title) {
    }

    private final AtomicReference<String> queryThread = new AtomicReference<>();
    private final AtomicReference<IntentContext> seenCaller = new AtomicReference<>();

    private IntentPipeline blocking(String dsl) {
        return IntentPipelines.builder()
                .llm((text, catalog) -> DslSources.of(dsl))
                .sqlBuilder(SqlBuilders.create(new PostgresDialect()))
                .executor(new QueryExecutor() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
                        queryThread.set(Thread.currentThread().getName());
                        return (List<T>) List.of(new Book(1, "Dune"));
                    }
                })
                .catalog(ServiceCatalogs.fromAnnotated(Book.class))
                .accessPolicy(new AccessPolicy() {
                    @Override
                    public boolean canQuery(ServiceDescriptor service, IntentContext caller) {
                        seenCaller.set(caller);
                        return true;
                    }
                })
                .maxRepairs(0)
                .build();
    }

    @Test
    void thePipelineRunsOnTheSchedulerNotOnTheSubscribersThread() {
        ReactiveIntentPipeline pipeline = ReactiveIntentPipelines.of(blocking("books"));

        IntentResponse<Book> response = pipeline.resolve(IntentRequests.of("books"), Book.class).block();

        assertThat(response.items()).extracting(Book::title).containsExactly("Dune");
        assertThat(queryThread.get()).startsWith("boundedElastic");
    }

    @Test
    void theCallerComesFromTheProvider() {
        ReactiveIntentPipeline pipeline = ReactiveIntentPipelines.of(blocking("books"), Schedulers.boundedElastic(),
                () -> Mono.just(IntentContexts.of("ann", "acme", Set.of("ROLE_CUSTOMER"))));

        pipeline.resolve(IntentRequests.of("books"), Book.class).block();

        assertThat(seenCaller.get().principal()).isEqualTo("ann");
        assertThat(seenCaller.get().tenant()).isEqualTo("acme");
    }

    @Test
    void aCallerOnTheRequestWins() {
        ReactiveIntentPipeline pipeline = ReactiveIntentPipelines.of(blocking("books"), Schedulers.boundedElastic(),
                () -> Mono.just(IntentContexts.of("ann", null, Set.of())));

        pipeline.resolve(IntentRequests.of("books", IntentContexts.of("svc", null, Set.of()))).block();

        assertThat(seenCaller.get().principal()).isEqualTo("svc");
    }

    @Test
    void errorsArriveWithTheirCode() {
        ReactiveIntentPipeline pipeline = ReactiveIntentPipelines.of(blocking("books | filter author == \"x\""));

        assertThatThrownBy(() -> pipeline.resolve(IntentRequests.of("books"), Book.class).block())
                .isInstanceOfSatisfying(IntentFlowException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.UNKNOWN_FIELD));
    }

    @Test
    void nothingRunsUntilSubscription() {
        ReactiveIntentPipeline pipeline = ReactiveIntentPipelines.of(blocking("books"));

        Mono<IntentResponse<Book>> pending = pipeline.resolve(IntentRequests.of("books"), Book.class);

        assertThat(queryThread.get()).isNull();
        assertThat(pipeline.explain(IntentRequests.of("books")).block().query()).startsWith("SELECT");
        assertThat(queryThread.get()).isNull();
        pending.block();
        assertThat(queryThread.get()).isNotNull();
    }
}
