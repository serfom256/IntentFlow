package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.api.factories.IntentRequests;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.pipeline.factories.IntentPipelineBuilder;
import io.github.serfom256.intentflow.core.pipeline.factories.IntentPipelines;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.core.testing.TestDialect;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntentPipelineBuilderTest {

    @IntentService(value = "books", uri = "/books/{{id}}", defaultSort = "id")
    record Book(@IntentField long id, @IntentField String title) {
    }

    private static IntentPipelineBuilder complete() {
        return IntentPipelines.builder().llm((text, catalog) -> DslSources.of("books"))
                .sqlBuilder(SqlBuilders.create(new TestDialect())).executor(new QueryExecutor() {
                    @Override
                    public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
                        return List.of();
                    }
                }).catalog(ServiceCatalogs.fromAnnotated(Book.class));
    }

    @Test
    void theFourRequiredPartsAreEnoughEverythingElseHasDefaults() {
        IntentPipeline pipeline = complete().build();

        assertThat(pipeline.resolve(IntentRequests.of("books"), Book.class).trace().query()).startsWith("SELECT");
    }

    @Test
    void buildNamesTheFirstMissingPart() {
        assertThatThrownBy(() -> IntentPipelines.builder().build()).isInstanceOf(IllegalStateException.class)
                .hasMessage("IntentPipeline requires llm(...) before build()");
        assertThatThrownBy(() -> IntentPipelines.builder().llm((text, catalog) -> DslSources.of("books"))
                .sqlBuilder(SqlBuilders.create(new TestDialect())).build())
                .hasMessage("IntentPipeline requires executor(...) before build()");
    }

    @Test
    void negativeRepairsAreRejected() {
        assertThatThrownBy(() -> complete().maxRepairs(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eachBuildGetsItsOwnCache() {
        IntentPipelineBuilder builder = complete();
        IntentPipeline first = builder.build();
        first.resolve(IntentRequests.of("books"), Book.class);

        assertThat(builder.build().resolve(IntentRequests.of("books"), Book.class).trace().cached()).isFalse();
        assertThat(first.resolve(IntentRequests.of("books"), Book.class).trace().cached()).isTrue();
    }
}
