package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.intentflow.core.api.IntentContext;
import io.github.serfom256.intentflow.core.api.factories.IntentContexts;
import io.github.serfom256.intentflow.core.api.factories.IntentRequests;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.pipeline.IntentPipeline;
import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import io.github.serfom256.intentflow.core.pipeline.LlmRateLimiter;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import io.github.serfom256.intentflow.core.pipeline.RateLimitExceededException;
import io.github.serfom256.intentflow.ratelimit.zookeeper.ZooKeeperRateLimiter;
import io.github.serfom256.intentflow.starter.autoconfigure.fixtures.Book;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.test.TestingServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZooKeeperRateLimitAutoConfigurationTest {

    private static final String FIXTURES = "io.github.serfom256.intentflow.starter.autoconfigure.fixtures";
    private static final IntentContext ANN = IntentContexts.of("ann", null, Set.of());

    private TestingServer zookeeper;

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IntentFlowAutoConfiguration.class))
            .withBean(LlmClient.class, () -> (text, catalog) -> DslSources.of("books"))
            .withBean(QueryExecutor.class, () -> new QueryExecutor() {
                @Override
                public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
                    return List.of();
                }
            }).withPropertyValues("intentflow.llm.model=m", "intentflow.catalog.packages=" + FIXTURES,
                    "intentflow.schema.introspect=false", "intentflow.dialect=postgresql",
                    "intentflow.llm.rate-limit.questions=1", "intentflow.llm.rate-limit.store=zookeeper");

    @BeforeEach
    void start() throws Exception {
        zookeeper = new TestingServer(true);
    }

    @AfterEach
    void stop() throws Exception {
        zookeeper.close();
    }

    @Test
    void theLimitIsKeptInZooKeeper() {
        runner.withPropertyValues("intentflow.llm.rate-limit.zookeeper.connect=" + zookeeper.getConnectString())
                .run(ctx -> {
                    assertThat(ctx.getBean(LlmRateLimiter.class)).isInstanceOf(ZooKeeperRateLimiter.class);
                    IntentPipeline pipeline = ctx.getBean(IntentPipeline.class);

                    pipeline.resolve(IntentRequests.of("books", ANN), Book.class);
                    assertThatThrownBy(() -> pipeline.resolve(IntentRequests.of("all the books", ANN), Book.class))
                            .isInstanceOf(RateLimitExceededException.class);
                    assertThat(ctx.getBean(CuratorFramework.class).getChildren().forPath("/intentflow/rate-limit"))
                            .hasSize(1);
                });
    }

    @Test
    void anApplicationsCuratorClientIsUsed() {
        CuratorFramework own = CuratorFrameworkFactory.newClient(zookeeper.getConnectString(), new RetryOneTime(50));
        own.start();
        try {
            runner.withBean(CuratorFramework.class, () -> own)
                    .withPropertyValues("intentflow.llm.rate-limit.zookeeper.path=/shop/limits").run(ctx -> {
                        ctx.getBean(IntentPipeline.class).resolve(IntentRequests.of("books", ANN), Book.class);

                        assertThat(own.getChildren().forPath("/shop/limits")).hasSize(1);
                    });
        } finally {
            own.close();
        }
    }

    @Test
    void withoutAConnectStringOrClientStartupSaysWhatToSet() {
        runner.run(ctx -> assertThat(ctx).hasFailed().getFailure().rootCause()
                .hasMessageContaining("intentflow.llm.rate-limit.zookeeper.connect"));
    }

    @Test
    void aSharedStoreWithoutItsModuleFailsAtStartup() {
        runner.withClassLoader(new FilteredClassLoader("io.github.serfom256.intentflow.ratelimit.zookeeper"))
                .withPropertyValues("intentflow.llm.rate-limit.zookeeper.connect=" + zookeeper.getConnectString())
                .run(ctx -> assertThat(ctx).hasFailed().getFailure().rootCause()
                        .hasMessageContaining("io.github.serfom256:intentflow-ratelimit-zookeeper on the classpath"));
    }

    @Test
    void theMemoryStoreNeedsNoModule() {
        runner.withClassLoader(
                new FilteredClassLoader("io.github.serfom256.intentflow.ratelimit.zookeeper", "org.apache.curator"))
                .withPropertyValues("intentflow.llm.rate-limit.store=memory").run(ctx -> assertThat(ctx).hasNotFailed()
                        .getBean(LlmRateLimiter.class).isNotInstanceOf(ZooKeeperRateLimiter.class));
    }
}
