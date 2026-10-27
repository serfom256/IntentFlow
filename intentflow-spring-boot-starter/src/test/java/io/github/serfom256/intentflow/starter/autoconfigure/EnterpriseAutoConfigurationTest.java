package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.intentflow.core.api.IntentContext;
import io.github.serfom256.intentflow.core.api.IntentRequest;
import io.github.serfom256.intentflow.core.api.IntentResponse;
import io.github.serfom256.intentflow.core.api.TokenUsage;
import io.github.serfom256.intentflow.core.api.factories.IntentContexts;
import io.github.serfom256.intentflow.core.api.factories.IntentRequests;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.pipeline.AccessDeniedException;
import io.github.serfom256.intentflow.core.pipeline.AccessPolicy;
import io.github.serfom256.intentflow.core.pipeline.IntentPipeline;
import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import io.github.serfom256.intentflow.core.pipeline.RateLimitExceededException;
import io.github.serfom256.intentflow.starter.autoconfigure.fixtures.Book;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnterpriseAutoConfigurationTest {

    private static final String FIXTURES = "io.github.serfom256.intentflow.starter.autoconfigure.fixtures";

    private final DataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
    private final List<String> executions = new ArrayList<>();

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IntentFlowAutoConfiguration.class))
            .withBean(DataSource.class, () -> dataSource)
            .withBean(LlmClient.class,
                    () -> (text, catalog) -> DslSources.of("books", new TokenUsage(120, 4), "local-model"))
            .withBean(QueryExecutor.class, () -> new QueryExecutor() {
                @Override
                public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
                    ConnectionHolder holder = (ConnectionHolder) TransactionSynchronizationManager
                            .getResource(dataSource);
                    executions.add("readOnly=" + TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                            + " timeout=" + (holder != null && holder.hasTimeout()));
                    return List.of();
                }
            }).withPropertyValues("intentflow.llm.model=m", "intentflow.catalog.packages=" + FIXTURES,
                    "intentflow.schema.introspect=false");

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anAccessPolicyBeanGuardsThePipeline() {
        runner.withBean(AccessPolicy.class, () -> new AccessPolicy() {
            @Override
            public boolean canQuery(ServiceDescriptor service, IntentContext caller) {
                return caller.hasRole("ROLE_LIBRARIAN");
            }
        }).run(ctx -> {
            IntentPipeline pipeline = ctx.getBean(IntentPipeline.class);

            assertThatThrownBy(() -> pipeline.resolve(IntentRequests.of("books"), Book.class))
                    .isInstanceOf(AccessDeniedException.class);
            IntentContext librarian = IntentContexts.of("lea", null, Set.of("ROLE_LIBRARIAN"));
            assertThat(pipeline.resolve(IntentRequests.of("books", librarian), Book.class).items()).isEmpty();
        });
    }

    @Test
    void theCallerComesFromSpringSecurity() {
        AtomicReference<IntentContext> seen = new AtomicReference<>();
        runner.withBean(AccessPolicy.class, () -> new AccessPolicy() {
            @Override
            public boolean canQuery(ServiceDescriptor service, IntentContext caller) {
                seen.set(caller);
                return true;
            }
        }).run(ctx -> {
            SecurityContextHolder.getContext()
                    .setAuthentication(new TestingAuthenticationToken("ann", "secret", "ROLE_CUSTOMER"));

            ctx.getBean(IntentPipeline.class).resolve(IntentRequests.of("books"), Book.class);

            assertThat(seen.get().principal()).hasValue("ann");
            assertThat(seen.get().roles()).containsExactly("ROLE_CUSTOMER");
        });
    }

    @Test
    void anIntentContextProviderBeanReplacesSpringSecurity() {
        AtomicReference<IntentContext> seen = new AtomicReference<>();
        runner.withBean(IntentContextProvider.class, () -> () -> IntentContexts.of("svc", "acme", Set.of()))
                .withBean(AccessPolicy.class, () -> new AccessPolicy() {
                    @Override
                    public boolean canQuery(ServiceDescriptor service, IntentContext caller) {
                        seen.set(caller);
                        return true;
                    }
                }).run(ctx -> {
                    ctx.getBean(IntentPipeline.class).resolve(IntentRequests.of("books"), Book.class);

                    assertThat(seen.get().tenant()).hasValue("acme");
                });
    }

    @Test
    void queriesRunReadOnlyWithATimeoutInsideAnIntentFlowTransaction() {
        runner.withBean(PlatformTransactionManager.class, () -> new DataSourceTransactionManager(dataSource))
                .run(ctx -> {
                    ctx.getBean(IntentPipeline.class).resolve(IntentRequests.of("books"), Book.class);

                    assertThat(executions).containsExactly("readOnly=true timeout=true");
                });
    }

    @Test
    void transactionsCanBeTurnedOff() {
        runner.withBean(PlatformTransactionManager.class, () -> new DataSourceTransactionManager(dataSource))
                .withPropertyValues("intentflow.sql.read-only=false", "intentflow.sql.statement-timeout=0").run(ctx -> {
                    ctx.getBean(IntentPipeline.class).resolve(IntentRequests.of("books"), Book.class);

                    assertThat(executions).containsExactly("readOnly=false timeout=false");
                });
    }

    @Test
    void requestsAndQueriesAreRecordedInMicrometer() {
        runner.withBean(MeterRegistry.class, SimpleMeterRegistry::new).run(ctx -> {
            ctx.getBean(IntentPipeline.class).resolve(IntentRequests.of("books"), Book.class);
            ctx.getBean(IntentPipeline.class).resolve(IntentRequests.of("books"), Book.class);
            MeterRegistry meters = ctx.getBean(MeterRegistry.class);

            assertThat(meters.get("intentflow.requests").tag("service", "books").tag("outcome", "answered").timer()
                    .count()).isEqualTo(2);
            assertThat(meters.get("intentflow.cache").tag("result", "hit").counter().count()).isEqualTo(1);
            assertThat(meters.get("intentflow.llm").tag("kind", "generate").timer().count()).isEqualTo(1);
            assertThat(meters.get("intentflow.query").tag("service", "books").timer().count()).isEqualTo(2);
            assertThat(meters.get("intentflow.llm.tokens").tag("model", "local-model").tag("type", "prompt").counter()
                    .count()).isEqualTo(120);
            assertThat(meters.get("intentflow.llm.tokens").tag("kind", "generate").tag("type", "completion").counter()
                    .count()).isEqualTo(4);
        });
    }

    @Test
    void aTenantSharesItsLlmRateLimit() {
        runner.withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withPropertyValues("intentflow.llm.rate-limit.questions=1", "intentflow.llm.rate-limit.per=tenant")
                .run(ctx -> {
                    IntentPipeline pipeline = ctx.getBean(IntentPipeline.class);
                    IntentContext ann = IntentContexts.of("ann", "acme", Set.of());
                    IntentContext bob = IntentContexts.of("bob", "acme", Set.of());

                    pipeline.resolve(IntentRequests.of("books", ann), Book.class);
                    pipeline.resolve(IntentRequests.of("books", bob), Book.class);

                    assertThatThrownBy(() -> pipeline.resolve(IntentRequests.of("all the books", bob), Book.class))
                            .isInstanceOf(RateLimitExceededException.class);
                    assertThat(ctx.getBean(MeterRegistry.class).get("intentflow.requests").tag("outcome", "limited")
                            .timer().count()).isEqualTo(1);
                });
    }

    @Test
    void thePipelineTakesASpringTypeReference() {
        runner.run(ctx -> {
            SpringIntentPipeline pipeline = ctx.getBean(SpringIntentPipeline.class);

            IntentResponse<Book> response = pipeline.resolve(IntentRequests.of("books"),
                    new ParameterizedTypeReference<Book>() {
                    });

            assertThat(response.type()).isEqualTo(Book.class);
            assertThat(SpringIntentPipeline.rawType(new ParameterizedTypeReference<List<Book>>() {
            })).isEqualTo(List.class);
        });
    }

    @Test
    void anIntentPipelineBeanReplacesTheStarterPipeline() {
        IntentPipeline own = new IntentPipeline() {
            @Override
            public IntentResponse<?> resolve(IntentRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> IntentResponse<T> resolve(IntentRequest request, Class<T> resultType) {
                throw new UnsupportedOperationException();
            }
        };

        runner.withBean(IntentPipeline.class, () -> own).run(ctx -> {
            assertThat(ctx).hasSingleBean(IntentPipeline.class).doesNotHaveBean(SpringIntentPipeline.class);
            assertThat(SpringIntentPipeline.of(own)).isNotSameAs(own);
        });
    }
}
