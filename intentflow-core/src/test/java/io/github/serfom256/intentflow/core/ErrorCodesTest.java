package io.github.serfom256.intentflow.core;

import io.github.serfom256.intentflow.core.api.factories.IntentRequests;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.pipeline.IntentPipeline;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import io.github.serfom256.intentflow.core.pipeline.RateLimitExceededException;
import io.github.serfom256.intentflow.core.pipeline.factories.IntentPipelines;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.core.testing.TestDialect;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class ErrorCodesTest {

    @IntentService(value = "books", uri = "/books/{{id}}", maxTake = 50, defaultSort = "id")
    record Book(@IntentField long id, @IntentField String title) {
    }

    private static IntentPipeline answering(String dsl) {
        return IntentPipelines.builder()
                .llm((text, catalog) -> DslSources.of(dsl))
                .sqlBuilder(SqlBuilders.create(new TestDialect()))
                .executor(new QueryExecutor() {
                    @Override
                    public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
                        return List.of();
                    }
                })
                .catalog(ServiceCatalogs.fromAnnotated(Book.class))
                .maxRepairs(0)
                .build();
    }

    private static IntentFlowException failure(String dsl) {
        return catchThrowableOfType(IntentFlowException.class,
                () -> answering(dsl).resolve(IntentRequests.of("q"), Book.class));
    }

    @Test
    void anUnknownFieldHasItsCodeAndTheField() {
        IntentFlowException error = failure("books | filter author == \"Herbert\"");

        assertThat(error.code()).isEqualTo(ErrorCode.UNKNOWN_FIELD);
        assertThat(error.code().value()).isEqualTo("intentflow.unknown-field");
        assertThat(error.details()).containsEntry("field", "author");
        assertThat(IntentFlowErrors.message(error, Locale.ENGLISH))
                .isEqualTo("There is no field \"author\" to search by.");
    }

    @Test
    void aDeclineCarriesTheReason() {
        IntentFlowException error = failure("unsupported \"books have no publisher\"");

        assertThat(error.code()).isEqualTo(ErrorCode.DECLINED);
        assertThat(IntentFlowErrors.message(error, Locale.ENGLISH))
                .isEqualTo("This question cannot be answered: books have no publisher");
    }

    @Test
    void limitsNameTheLimit() {
        IntentFlowException error = failure("books | take 500");

        assertThat(error.code()).isEqualTo(ErrorCode.LIMIT_EXCEEDED);
        assertThat(IntentFlowErrors.message(error, Locale.ENGLISH))
                .isEqualTo("The question asks for more than allowed: at most 50.");
    }

    @Test
    void anApplicationBundleTranslatesAndMissingKeysFallBackToEnglish() {
        IntentFlowException unknown = failure("books | filter author == \"Herbert\"");
        IntentFlowException limited = new RateLimitExceededException("limited", Duration.ofSeconds(42));

        assertThat(IntentFlowErrors.message(unknown, Locale.GERMAN))
                .isEqualTo("Es gibt kein Feld \"author\", nach dem gesucht werden kann.");
        assertThat(IntentFlowErrors.message(limited, Locale.GERMAN))
                .isEqualTo("Too many new questions. Try again in 42 seconds.");
    }

    @Test
    void everyCodeHasAnEnglishMessage() {
        ResourceBundle english = ResourceBundle.getBundle(IntentFlowErrors.BUNDLE, Locale.ROOT);

        for (ErrorCode code : ErrorCode.values()) {
            assertThat(english.containsKey(code.value())).as(code.value()).isTrue();
        }
    }
}
