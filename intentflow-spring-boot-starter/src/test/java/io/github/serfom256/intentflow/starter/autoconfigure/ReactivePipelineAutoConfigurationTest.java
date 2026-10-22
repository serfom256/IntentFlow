package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.intentflow.core.api.IntentContext;
import io.github.serfom256.intentflow.core.api.IntentResponse;
import io.github.serfom256.intentflow.core.api.factories.IntentRequests;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.pipeline.AccessPolicy;
import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import io.github.serfom256.intentflow.reactive.ReactiveIntentPipeline;
import io.github.serfom256.intentflow.starter.autoconfigure.fixtures.Book;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ReactivePipelineAutoConfigurationTest {

    private static final String FIXTURES = "io.github.serfom256.intentflow.starter.autoconfigure.fixtures";

    private final AtomicReference<IntentContext> seen = new AtomicReference<>();

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IntentFlowAutoConfiguration.class))
            .withBean(LlmClient.class, () -> (text, catalog) -> DslSources.of("books"))
            .withBean(QueryExecutor.class, () -> new QueryExecutor() {
                @Override
                public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
                    return List.of();
                }
            })
            .withBean(AccessPolicy.class, () -> new AccessPolicy() {
                @Override
                public boolean canQuery(ServiceDescriptor service, IntentContext caller) {
                    seen.set(caller);
                    return true;
                }
            })
            .withPropertyValues("intentflow.llm.model=m", "intentflow.catalog.packages=" + FIXTURES,
                    "intentflow.schema.introspect=false", "intentflow.dialect=postgresql");

    @Test
    void theReactivePipelineTakesTheCallerFromTheReactiveSecurityContext() {
        runner.run(ctx -> {
            ReactiveIntentPipeline pipeline = ctx.getBean(ReactiveIntentPipeline.class);

            pipeline.resolve(IntentRequests.of("books"), Book.class)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                            new TestingAuthenticationToken("ann", "x", "ROLE_CUSTOMER")))
                    .block();

            assertThat(seen.get().principal()).isEqualTo("ann");
            assertThat(seen.get().roles()).containsExactly("ROLE_CUSTOMER");
        });
    }

    @Test
    void withoutASignedInUserTheCallerIsAnonymous() {
        runner.run(ctx -> {
            ctx.getBean(ReactiveIntentPipeline.class).resolve(IntentRequests.of("books"), Book.class).block();

            assertThat(seen.get().principal()).isNull();
        });
    }

    @Test
    void withoutReactorThereIsNoReactivePipeline() {
        runner.withClassLoader(new FilteredClassLoader("reactor.core"))
                .run(ctx -> assertThat(ctx).hasNotFailed().doesNotHaveBean(ReactiveIntentPipeline.class));
    }

    @Test
    void withoutSpringSecurityTheReactivePipelineRunsAnonymously() {
        runner.withClassLoader(new FilteredClassLoader("org.springframework.security")).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            ctx.getBean(ReactiveIntentPipeline.class).resolve(IntentRequests.of("books"), Book.class).block();

            assertThat(seen.get().principal()).isNull();
        });
    }

    @Test
    void theReactivePipelineTakesASpringTypeReference() {
        runner.run(ctx -> {
            IntentResponse<Book> response = ctx.getBean(SpringReactiveIntentPipeline.class)
                    .resolve(IntentRequests.of("books"), new ParameterizedTypeReference<Book>() {
                    }).block();

            assertThat(response.type()).isEqualTo(Book.class);
        });
    }
}
