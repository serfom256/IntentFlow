package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.api.IntentContext;
import io.github.serfom256.intentflow.core.api.UserText;
import io.github.serfom256.intentflow.core.api.factories.IntentContexts;
import io.github.serfom256.intentflow.core.api.factories.IntentRequests;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.DslSource;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.pipeline.factories.IntentPipelines;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.core.testing.TestDialect;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PipelineTracerTest {

    @IntentService(value = "books", uri = "/books/{{id}}", defaultSort = "id")
    record Book(@IntentField long id, @IntentField String title) {
    }

    private static final ServiceCatalog CATALOG = ServiceCatalogs.fromAnnotated(Book.class);

    private final List<String> spans = new ArrayList<>();
    private RuntimeException queryFailure;

    private final PipelineTracer tracer = new PipelineTracer() {
        private int depth;

        @Override
        public Span start(Stage stage) {
            spans.add("  ".repeat(depth++) + stage);
            return (service, outcome, error) -> {
                depth--;
                spans.add("  ".repeat(depth) + "/" + stage + " " + (service == null ? "-" : service.value()) + " "
                        + outcome + (error == null ? "" : " " + error.getClass().getSimpleName()));
            };
        }
    };

    private IntentPipeline pipeline(String... answers) {
        Deque<String> queue = new ArrayDeque<>(List.of(answers));
        LlmClient llm = new LlmClient() {
            @Override
            public DslSource generateDsl(UserText text, ServiceCatalog catalog) {
                return DslSources.of(queue.poll());
            }

            @Override
            public DslSource repairDsl(UserText text, ServiceCatalog catalog, DslSource failed, String error) {
                return DslSources.of(queue.poll());
            }
        };
        QueryExecutor executor = new QueryExecutor() {
            @Override
            public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
                if (queryFailure != null) {
                    throw queryFailure;
                }
                return List.of();
            }
        };
        return IntentPipelines.builder().llm(llm).sqlBuilder(SqlBuilders.create(new TestDialect())).executor(executor)
                .catalog(CATALOG).tracer(tracer).build();
    }

    @Test
    void everyStageIsASpanInsideTheRequest() {
        pipeline("books | filter author == \"x\"", "books | filter title == \"x\"")
                .resolve(IntentRequests.of("books by x"), Book.class);

        assertThat(spans).containsExactly("REQUEST", "  GENERATE", "  /GENERATE books ok", "  VALIDATE",
                "  /VALIDATE books rejected ValidationException", "  REPAIR", "  /REPAIR books ok", "  VALIDATE",
                "  /VALIDATE books ok", "  BUILD", "  /BUILD books ok", "  QUERY", "  /QUERY books ok",
                "/REQUEST books answered");
    }

    @Test
    void aCachedQuestionSkipsTheLlm() {
        IntentPipeline pipeline = pipeline("books");
        pipeline.resolve(IntentRequests.of("books"));
        spans.clear();

        pipeline.resolve(IntentRequests.of("books"));

        assertThat(spans).filteredOn(span -> !span.startsWith("/") && !span.startsWith("  /"))
                .containsExactly("REQUEST", "  VALIDATE", "  BUILD", "  QUERY");
    }

    @Test
    void aDeclineEndsTheRequestWithoutAnError() {
        assertThatThrownBy(() -> pipeline("unsupported \"no authors\"").resolve(IntentRequests.of("q")))
                .isInstanceOf(UnsupportedRequestException.class);

        assertThat(spans).contains("  /VALIDATE - declined", "/REQUEST - declined");
    }

    @Test
    void aFailingQueryEndsItsSpanAndTheRequestWithTheError() {
        queryFailure = new IllegalStateException("database is down");

        assertThatThrownBy(() -> pipeline("books").resolve(IntentRequests.of("q"))).isSameAs(queryFailure);

        assertThat(spans).endsWith("  /QUERY books error IllegalStateException",
                "/REQUEST - failed IllegalStateException");
    }

    @Test
    void aDeniedRequestIsTraced() {
        IntentContext anyone = IntentContexts.anonymous();
        IntentPipeline pipeline = IntentPipelines.builder().llm((text, catalog) -> DslSources.of("books"))
                .sqlBuilder(SqlBuilders.create(new TestDialect())).executor(new QueryExecutor() {
                    @Override
                    public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
                        return List.of();
                    }
                }).catalog(CATALOG).accessPolicy(new AccessPolicy() {
                    @Override
                    public boolean canQuery(ServiceDescriptor service, IntentContext caller) {
                        return false;
                    }
                }).defaultCaller(() -> anyone).tracer(tracer).build();

        assertThatThrownBy(() -> pipeline.resolve(IntentRequests.of("q"), Book.class))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(spans).containsExactly("REQUEST", "/REQUEST books denied");
    }
}
