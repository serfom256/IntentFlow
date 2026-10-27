package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.api.IntentResponse;
import io.github.serfom256.intentflow.core.api.UserText;
import io.github.serfom256.intentflow.core.api.factories.IntentRequests;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.DslSource;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
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

class RepairTest {

    @IntentService(value = "books", uri = "/books/{{id}}", defaultSort = "id")
    record Book(@IntentField long id, @IntentField String title) {
    }

    private static final ServiceCatalog CATALOG = ServiceCatalogs.fromAnnotated(Book.class);

    private final List<String> errors = new ArrayList<>();
    private int generated;

    private IntentPipeline pipeline(int maxRepairs, String... answers) {
        Deque<String> queue = new ArrayDeque<>(List.of(answers));
        LlmClient llm = new LlmClient() {
            @Override
            public DslSource generateDsl(UserText text, ServiceCatalog catalog) {
                generated++;
                return DslSources.of(queue.poll());
            }

            @Override
            public DslSource repairDsl(UserText text, ServiceCatalog catalog, DslSource failed, String error) {
                errors.add(failed.value() + " -> " + error);
                return DslSources.of(queue.poll());
            }
        };
        QueryExecutor executor = new QueryExecutor() {
            @Override
            public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
                return List.of();
            }
        };
        return IntentPipelines.builder().llm(llm).sqlBuilder(SqlBuilders.create(new TestDialect())).executor(executor)
                .catalog(CATALOG).maxRepairs(maxRepairs).build();
    }

    @Test
    void anInvalidAnswerIsCorrectedWithTheValidationError() {
        IntentPipeline pipeline = pipeline(1, "books | filter author == \"x\"", "books | filter title == \"x\"");

        IntentResponse<?> response = pipeline.resolve(IntentRequests.of("books by x"));

        assertThat(response.trace().dsl()).isEqualTo("books | filter title == \"x\"");
        assertThat(response.trace().repairs()).isEqualTo(1);
        assertThat(errors).singleElement().asString()
                .startsWith("books | filter author == \"x\" -> field 'author' is not declared");
        assertThat(pipeline.resolve(IntentRequests.of("books by x")).trace().cached()).isTrue();
        assertThat(generated).isEqualTo(1);
    }

    @Test
    void parseErrorsAreCorrectedToo() {
        IntentPipeline pipeline = pipeline(1, "books | filter ((", "books | take 1");

        assertThat(pipeline.resolve(IntentRequests.of("one book")).trace().repairs()).isEqualTo(1);
    }

    @Test
    void givesUpAfterTheConfiguredNumberOfRepairs() {
        IntentPipeline pipeline = pipeline(2, "books | filter a == 1", "books | filter b == 1",
                "books | filter c == 1");

        assertThatThrownBy(() -> pipeline.resolve(IntentRequests.of("q"))).isInstanceOf(ValidationException.class)
                .hasMessageContaining("'c'");
        assertThat(errors).hasSize(2);
    }

    @Test
    void zeroRepairsFailsOnTheFirstError() {
        IntentPipeline pipeline = pipeline(0, "books | filter author == \"x\"");

        assertThatThrownBy(() -> pipeline.resolve(IntentRequests.of("q"))).isInstanceOf(ValidationException.class);
        assertThat(errors).isEmpty();
    }

    @Test
    void declinesAreAnswersAndAreNotRetried() {
        IntentPipeline pipeline = pipeline(1, "unsupported \"no authors\"");

        assertThatThrownBy(() -> pipeline.resolve(IntentRequests.of("q")))
                .isInstanceOf(UnsupportedRequestException.class);
        assertThat(errors).isEmpty();
    }

    @Test
    void aCorrectionMayDeclineInstead() {
        IntentPipeline pipeline = pipeline(1, "books | filter author == \"x\"", "unsupported \"books have no author\"");

        assertThatThrownBy(() -> pipeline.resolve(IntentRequests.of("q")))
                .isInstanceOf(UnsupportedRequestException.class).hasMessageContaining("no author");
    }
}
