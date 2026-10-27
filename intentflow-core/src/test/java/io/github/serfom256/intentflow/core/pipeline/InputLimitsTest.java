package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ErrorCode;
import io.github.serfom256.intentflow.core.api.IntentResponse;
import io.github.serfom256.intentflow.core.api.factories.IntentRequests;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.pipeline.factories.AstValidators;
import io.github.serfom256.intentflow.core.pipeline.factories.IntentPipelineBuilder;
import io.github.serfom256.intentflow.core.pipeline.factories.IntentPipelines;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.core.testing.TestDialect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InputLimitsTest {

    @IntentService(value = "books", uri = "/books/{{id}}", defaultSort = "price", maxTake = 10)
    record Book(@IntentField long id, @IntentField String title, @IntentField double price) {
    }

    private final AtomicInteger llmCalls = new AtomicInteger();

    private IntentPipeline pipeline(String dsl) {
        return pipeline(dsl, IntentPipelines.builder());
    }

    private IntentPipeline pipeline(String dsl, IntentPipelineBuilder builder) {
        return builder.llm((text, catalog) -> {
            llmCalls.incrementAndGet();
            return DslSources.of(dsl);
        }).sqlBuilder(SqlBuilders.create(new TestDialect())).executor(new QueryExecutor() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
                return (List<T>) LongStream.rangeClosed(1, 11).mapToObj(id -> new Book(id, "b" + id, id)).toList();
            }
        }).catalog(ServiceCatalogs.fromAnnotated(Book.class)).validator(AstValidators.create(20)).maxRepairs(0).build();
    }

    @ParameterizedTest
    @ValueSource(strings = { "(", "not ", "price > 1 and ", "price + " })
    void deeplyNestedOrVeryLongAnswersAreRejectedAsParseErrors(String repeated) {
        String filter = switch (repeated) {
        case "(" -> "(".repeat(20_000) + "price > 1" + ")".repeat(20_000);
        case "price + " -> "price + ".repeat(20_000) + "price > 1";
        default -> repeated.repeat(20_000) + "price > 1";
        };

        assertThatThrownBy(() -> pipeline("books | filter " + filter).resolve(IntentRequests.of("q"), Book.class))
                .isInstanceOfSatisfying(DslParseException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.PARSE));
    }

    @Test
    void ordinaryNestingStillParses() {
        String filter = "(".repeat(10) + "not (price > 1 and title ~ \"a\") or price * 2 + 1 > 3" + ")".repeat(10);

        assertThat(pipeline("books | filter " + filter).resolve(IntentRequests.of("q"), Book.class).items())
                .isNotEmpty();
    }

    @Test
    void aQuestionOverTheLengthLimitNeverReachesTheModel() {
        IntentPipeline pipeline = pipeline("books", IntentPipelines.builder().maxQuestionLength(50));

        assertThatThrownBy(() -> pipeline.resolve(IntentRequests.of("x".repeat(51)), Book.class))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.LIMIT_EXCEEDED);
                    assertThat(e.details()).containsEntry("limit", 50);
                });
        assertThat(llmCalls).hasValue(0);
        assertThat(pipeline.resolve(IntentRequests.of("x".repeat(50)), Book.class).items()).isNotEmpty();
    }

    @Test
    void anOffsetCursorCannotSkipFurtherThanMaxSkip() {
        IntentPipeline pipeline = pipeline("books | sort title | take 10");
        IntentResponse<Book> first = pipeline.resolve(IntentRequests.page("q", null), Book.class);
        String cursor = first.nextCursor().orElseThrow();
        String[] parts = new String(Base64.getUrlDecoder().decode(cursor.substring(0, cursor.indexOf('.'))),
                StandardCharsets.UTF_8).split("\\|");
        parts[3] = "1000000";
        String forged = Base64.getUrlEncoder().withoutPadding().encodeToString(
                String.join("|", parts).getBytes(StandardCharsets.UTF_8)) + cursor.substring(cursor.indexOf('.'));

        assertThat(pipeline.resolve(IntentRequests.page("q", first.nextCursor().orElseThrow()), Book.class).items())
                .hasSize(10);
        assertThatThrownBy(() -> pipeline.resolve(IntentRequests.page("q", forged), Book.class)).isInstanceOfSatisfying(
                ValidationException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.LIMIT_EXCEEDED));
    }
}
