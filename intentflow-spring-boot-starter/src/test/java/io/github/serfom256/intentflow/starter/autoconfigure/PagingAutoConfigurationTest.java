package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.intentflow.core.api.IntentResponse;
import io.github.serfom256.intentflow.core.api.factories.IntentRequests;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.pipeline.IntentPipeline;
import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import io.github.serfom256.intentflow.starter.autoconfigure.fixtures.Book;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

class PagingAutoConfigurationTest {

    private static final String FIXTURES = "io.github.serfom256.intentflow.starter.autoconfigure.fixtures";
    private static final String KEY = "intentflow.paging.cursor-key=a key every instance of the shop shares";

    private final AtomicInteger llmCalls = new AtomicInteger();

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IntentFlowAutoConfiguration.class))
            .withBean(LlmClient.class, () -> (text, catalog) -> {
                llmCalls.incrementAndGet();
                return DslSources.of("books | sort title | take 2");
            }).withBean(QueryExecutor.class, () -> new QueryExecutor() {
                @Override
                @SuppressWarnings("unchecked")
                public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
                    return (List<T>) LongStream.rangeClosed(1, 3).mapToObj(id -> new Book(id, "t" + id, BigDecimal.ONE))
                            .toList();
                }
            }).withPropertyValues("intentflow.llm.model=m", "intentflow.catalog.packages=" + FIXTURES,
                    "intentflow.schema.introspect=false", "intentflow.dialect=postgresql");

    @Test
    void instancesWithTheConfiguredKeyContinueEachOthersPages() {
        AtomicReference<String> cursor = new AtomicReference<>();
        runner.withPropertyValues(KEY).run(ctx -> cursor.set(ctx.getBean(IntentPipeline.class)
                .resolve(IntentRequests.page("books", null), Book.class).nextCursor().orElseThrow()));
        llmCalls.set(0);

        runner.withPropertyValues(KEY).run(ctx -> {
            IntentResponse<Book> next = ctx.getBean(IntentPipeline.class)
                    .resolve(IntentRequests.page("books", cursor.get()), Book.class);

            assertThat(next.items()).hasSize(2);
            assertThat(llmCalls).hasValue(0);
        });
    }

    @Test
    void aShortKeyFailsAtStartup() {
        runner.withPropertyValues("intentflow.paging.cursor-key=short").run(ctx -> assertThat(ctx).hasFailed()
                .getFailure().rootCause().hasMessageContaining("cursorKey must have at least 32 bytes"));
    }
}
