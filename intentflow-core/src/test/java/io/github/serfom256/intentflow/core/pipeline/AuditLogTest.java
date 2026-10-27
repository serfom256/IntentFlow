package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.api.IntentContext;
import io.github.serfom256.intentflow.core.api.TokenUsage;
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
import io.github.serfom256.intentflow.core.catalog.factories.ServiceNames;
import io.github.serfom256.intentflow.core.pipeline.PipelineListener.Outcome;
import io.github.serfom256.intentflow.core.pipeline.factories.IntentPipelineBuilder;
import io.github.serfom256.intentflow.core.pipeline.factories.IntentPipelines;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.core.testing.TestDialect;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditLogTest {

    @IntentService(value = "books", uri = "/books/{{id}}", defaultSort = "id")
    record Book(@IntentField long id, @IntentField String title) {
    }

    private static final ServiceCatalog CATALOG = ServiceCatalogs.fromAnnotated(Book.class);
    private static final IntentContext ANN = IntentContexts.of("ann", "acme", Set.of());

    private final List<QuestionAudit> audits = new ArrayList<>();

    private IntentPipelineBuilder pipeline(String... answers) {
        Deque<String> queue = new ArrayDeque<>(List.of(answers));
        LlmClient llm = new LlmClient() {
            @Override
            public DslSource generateDsl(UserText text, ServiceCatalog catalog) {
                return DslSources.of(queue.poll(), new TokenUsage(100, 5), "m");
            }

            @Override
            public DslSource repairDsl(UserText text, ServiceCatalog catalog, DslSource failed, String error) {
                return DslSources.of(queue.poll(), new TokenUsage(110, 6), "m");
            }
        };
        return IntentPipelines.builder().llm(llm).sqlBuilder(SqlBuilders.create(new TestDialect()))
                .executor(new QueryExecutor() {
                    @Override
                    public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
                        return List.of();
                    }
                }).catalog(CATALOG).audit(new AuditLog() {
                    @Override
                    public void question(QuestionAudit audit) {
                        audits.add(audit);
                    }
                });
    }

    @Test
    void anAnsweredQuestionRecordsWhoWhatAndHow() {
        pipeline("books | filter title == \"Dune\"").build().resolve(IntentRequests.of("dune", ANN), Book.class);

        QuestionAudit audit = audits.getFirst();
        assertThat(audit.principal()).isEqualTo("ann");
        assertThat(audit.tenant()).isEqualTo("acme");
        assertThat(audit.question()).isEqualTo("dune");
        assertThat(audit.service()).isEqualTo(ServiceNames.of("books"));
        assertThat(audit.outcome()).isEqualTo(Outcome.ANSWERED);
        assertThat(audit.dsl()).isEqualTo("books | filter title == \"Dune\"");
        assertThat(audit.sql()).contains("SELECT");
        assertThat(audit.parameters()).containsExactly("Dune");
        assertThat(audit.rows()).isZero();
        assertThat(audit.tokens()).isEqualTo(new TokenUsage(100, 5));
        assertThat(audit.error()).isNull();
    }

    @Test
    void aRejectedQuestionRecordsTheLastDslAndTheError() {
        IntentPipeline pipeline = pipeline("books | filter author == \"x\"", "books | filter pages > 1").maxRepairs(1)
                .build();

        assertThatThrownBy(() -> pipeline.resolve(IntentRequests.of("q", ANN))).isInstanceOf(ValidationException.class);

        QuestionAudit audit = audits.getFirst();
        assertThat(audit.outcome()).isEqualTo(Outcome.REJECTED);
        assertThat(audit.dsl()).isEqualTo("books | filter pages > 1");
        assertThat(audit.sql()).isNull();
        assertThat(audit.tokens()).isEqualTo(new TokenUsage(210, 11));
        assertThat(audit.error()).contains("pages");
    }

    @Test
    void aDeniedCallerIsRecorded() {
        IntentPipeline pipeline = pipeline("books").accessPolicy(new AccessPolicy() {
            @Override
            public boolean canQuery(ServiceDescriptor service, IntentContext caller) {
                return false;
            }
        }).build();

        assertThatThrownBy(() -> pipeline.resolve(IntentRequests.of("q", ANN), Book.class))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(audits).singleElement().satisfies(audit -> {
            assertThat(audit.outcome()).isEqualTo(Outcome.DENIED);
            assertThat(audit.principal()).isEqualTo("ann");
            assertThat(audit.dsl()).isNull();
        });
    }

    @Test
    void explainIsNotAudited() {
        pipeline("books").build().explain(IntentRequests.of("q", ANN));

        assertThat(audits).isEmpty();
    }

    @Test
    void aFailingAuditLogDoesNotFailTheQuestion() {
        IntentPipeline pipeline = pipeline("books").audit(new AuditLog() {
            @Override
            public void question(QuestionAudit audit) {
                throw new IllegalStateException("audit store is down");
            }
        }).build();

        assertThat(pipeline.resolve(IntentRequests.of("q", ANN)).items()).isEmpty();
    }
}
