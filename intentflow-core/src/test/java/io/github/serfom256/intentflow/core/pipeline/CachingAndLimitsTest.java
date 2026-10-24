package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.api.IntentTrace;
import io.github.serfom256.intentflow.core.api.factories.IntentRequests;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceDescriptors;
import io.github.serfom256.intentflow.core.pipeline.factories.AstValidators;
import io.github.serfom256.intentflow.core.pipeline.factories.IntentPipelines;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.core.testing.TestDialect;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CachingAndLimitsTest {

    @IntentService(value = "books", uri = "/books/{{id}}", defaultSort = "id")
    record Book(@IntentField long id, @IntentField String title) {
    }

    private static final ServiceCatalog CATALOG = ServiceCatalogs.of(ServiceDescriptors.fromAnnotated(Book.class));

    private final QueryExecutor executor = new QueryExecutor() {
        @Override
        public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
            return List.of();
        }
    };

    private IntentPipeline pipeline(Deque<String> answers, AtomicInteger calls, AstValidator validator) {
        return IntentPipelines.builder()
                .llm((text, catalog) -> {
                    calls.incrementAndGet();
                    return DslSources.of(answers.size() > 1 ? answers.poll() : answers.peek());
                })
                .sqlBuilder(SqlBuilders.create(new TestDialect()))
                .executor(executor)
                .catalog(CATALOG)
                .validator(validator)
                .maxRepairs(0)
                .build();
    }

    @Test
    void explainCompilesWithoutRunningAndCachesTheTranslation() {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger executed = new AtomicInteger();
        QueryExecutor counting = new QueryExecutor() {
            @Override
            public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
                executed.incrementAndGet();
                return List.of();
            }
        };
        IntentPipeline pipeline = IntentPipelines.builder()
                .llm((text, catalog) -> {
                    calls.incrementAndGet();
                    return DslSources.of("books | filter title == \"Dune\"");
                })
                .sqlBuilder(SqlBuilders.create(new TestDialect()))
                .executor(counting)
                .catalog(CATALOG)
                .build();

        IntentTrace trace = pipeline.explain(IntentRequests.of("dune"), Book.class);

        assertThat(trace.dsl()).isEqualTo("books | filter title == \"Dune\"");
        assertThat(trace.cached()).isFalse();
        assertThat(trace.query()).isEqualTo(
                "SELECT * FROM \"book\" WHERE LOWER(\"title\") = LOWER(?) ORDER BY \"id\" ASC LIMIT 50");
        assertThat(trace.parameters()).containsExactly("Dune");
        assertThat(executed).hasValue(0);

        assertThat(pipeline.resolve(IntentRequests.of("dune"), Book.class).trace().cached()).isTrue();
        assertThat(calls).hasValue(1);
        assertThat(executed).hasValue(1);
    }

    @Test
    void invalidTranslationsAreNotCachedSoARetryAsksTheLlmAgain() {
        AtomicInteger calls = new AtomicInteger();
        IntentPipeline pipeline = pipeline(
                new ArrayDeque<>(List.of("books | filter ((", "books | filter title ~ \"x\"")),
                calls, AstValidators.create());

        assertThatThrownBy(() -> pipeline.resolve(IntentRequests.of("x books"))).isInstanceOf(DslParseException.class);
        assertThat(pipeline.resolve(IntentRequests.of("x books")).trace().cached()).isFalse();
        assertThat(pipeline.resolve(IntentRequests.of("x books")).trace().cached()).isTrue();
        assertThat(calls).hasValue(2);
    }

    @Test
    void validationFailuresAreNotCachedEither() {
        AtomicInteger calls = new AtomicInteger();
        IntentPipeline pipeline = pipeline(new ArrayDeque<>(List.of("books | filter author == \"x\"", "books")),
                calls, AstValidators.create());

        assertThatThrownBy(() -> pipeline.resolve(IntentRequests.of("by author")))
                .isInstanceOf(ValidationException.class);
        pipeline.resolve(IntentRequests.of("by author"));
        assertThat(calls).hasValue(2);
    }

    @Test
    void declinesAreCachedBecauseTheyAreAnswers() {
        AtomicInteger calls = new AtomicInteger();
        IntentPipeline pipeline = pipeline(new ArrayDeque<>(List.of("unsupported \"no authors\"")),
                calls, AstValidators.create());

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> pipeline.resolve(IntentRequests.of("authors")))
                    .isInstanceOf(UnsupportedRequestException.class);
        }
        assertThat(calls).hasValue(1);
    }

    @Test
    void skipIsBounded() {
        IntentPipeline pipeline = pipeline(new ArrayDeque<>(List.of("books | skip 50000000")), new AtomicInteger(),
                AstValidators.create(1000));

        assertThatThrownBy(() -> pipeline.resolve(IntentRequests.of("far away")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("skip value 50000000 exceeds the maximum of 1000");
    }
}
