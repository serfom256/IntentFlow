package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.api.IntentTrace;
import io.github.serfom256.intentflow.core.api.TokenUsage;
import io.github.serfom256.intentflow.core.api.UserText;
import io.github.serfom256.intentflow.core.api.factories.IntentRequests;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.DslSource;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.pipeline.factories.IntentPipelineBuilder;
import io.github.serfom256.intentflow.core.pipeline.factories.IntentPipelines;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.core.testing.TestDialect;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TokenUsageTest {

    @IntentService(value = "books", uri = "/books/{{id}}", defaultSort = "id")
    record Book(@IntentField long id, @IntentField String title) {
    }

    private static final ServiceCatalog CATALOG = ServiceCatalogs.fromAnnotated(Book.class);

    private final List<String> events = new ArrayList<>();

    private IntentPipeline pipeline() {
        return builder().build();
    }

    private IntentPipelineBuilder builder() {
        LlmClient llm = new LlmClient() {
            @Override
            public DslSource generateDsl(UserText text, ServiceCatalog catalog) {
                return DslSources.of("books | filter author == \"x\"", new TokenUsage(900, 20), "big");
            }

            @Override
            public DslSource repairDsl(UserText text, ServiceCatalog catalog, DslSource failed, String error) {
                return DslSources.of("books | filter title == \"x\"", new TokenUsage(950, 12), "small");
            }
        };
        PipelineListener listener = new PipelineListener() {
            @Override
            public void tokens(@Nullable String model, boolean repair, TokenUsage usage) {
                events.add(model + (repair ? " repair " : " generate ") + usage.total());
            }
        };
        return IntentPipelines.builder()
                .llm(llm)
                .sqlBuilder(SqlBuilders.create(new TestDialect()))
                .executor(new QueryExecutor() {
                    @Override
                    public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
                        return List.of();
                    }
                })
                .catalog(CATALOG)
                .listener(listener);
    }

    @Test
    void theTraceSumsTheFirstAnswerAndTheRepair() {
        IntentTrace trace = pipeline().resolve(IntentRequests.of("books by x")).trace();

        assertThat(trace.repairs()).isEqualTo(1);
        assertThat(trace.tokens()).isEqualTo(new TokenUsage(1850, 32));
        assertThat(events).containsExactly("big generate 920", "small repair 962");
    }

    @Test
    void aCachedAnswerUsesNoTokens() {
        IntentPipeline pipeline = pipeline();
        pipeline.resolve(IntentRequests.of("books by x"));
        events.clear();

        IntentTrace trace = pipeline.resolve(IntentRequests.of("books by x")).trace();

        assertThat(trace.cached()).isTrue();
        assertThat(trace.tokens()).isEqualTo(TokenUsage.NONE);
        assertThat(events).isEmpty();
    }

    @Test
    void explainReportsTheTokensToo() {
        assertThat(pipeline().explain(IntentRequests.of("books by x")).tokens().total()).isEqualTo(1882);
    }

    @Test
    void spansCarryTheTokens() {
        List<String> spans = new ArrayList<>();
        PipelineTracer tracer = stage -> new PipelineTracer.Span() {
            @Override
            public void usage(TokenUsage tokens, @Nullable String model) {
                spans.add(stage + " " + tokens.total() + " " + model);
            }

            @Override
            public void end(@Nullable ServiceName service, String outcome, @Nullable Throwable error) {
            }
        };

        builder().tracer(tracer).build().resolve(IntentRequests.of("books by x"));

        assertThat(spans).containsExactly("GENERATE 920 big", "REPAIR 962 small", "REQUEST 1882 null");
    }
}
