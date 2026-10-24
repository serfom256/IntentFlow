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
import io.github.serfom256.intentflow.ratelimit.jdbc.JdbcRateLimiter;
import io.github.serfom256.intentflow.ratelimit.redis.RedisRateLimiter;
import io.github.serfom256.intentflow.starter.autoconfigure.fixtures.Book;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.GenericContainer;

import javax.sql.DataSource;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SharedRateLimitAutoConfigurationTest {

    private static final String FIXTURES = "io.github.serfom256.intentflow.starter.autoconfigure.fixtures";
    private static final IntentContext ANN = IntentContexts.of("ann", null, Set.of());
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.8.2-alpine")
            .withExposedPorts(6379);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IntentFlowAutoConfiguration.class))
            .withBean(LlmClient.class, () -> (text, catalog) -> DslSources.of("books"))
            .withBean(QueryExecutor.class, () -> new QueryExecutor() {
                @Override
                public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
                    return List.of();
                }
            })
            .withPropertyValues("intentflow.llm.model=m", "intentflow.catalog.packages=" + FIXTURES,
                    "intentflow.schema.introspect=false", "intentflow.dialect=postgresql",
                    "intentflow.llm.rate-limit.questions=1");

    @AfterAll
    static void stop() {
        REDIS.stop();
    }

    private static void secondQuestionIsLimited(IntentPipeline pipeline) {
        pipeline.resolve(IntentRequests.of("books", ANN), Book.class);
        assertThatThrownBy(() -> pipeline.resolve(IntentRequests.of("all the books", ANN), Book.class))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void theJdbcStoreKeepsTheLimitInTheApplicationsDatabase() {
        DataSource dataSource = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1",
                "sa", "");
        runner.withBean(DataSource.class, () -> dataSource)
                .withPropertyValues("intentflow.llm.rate-limit.store=jdbc").run(ctx -> {
                    assertThat(ctx.getBean(LlmRateLimiter.class)).isInstanceOf(JdbcRateLimiter.class);
                    secondQuestionIsLimited(ctx.getBean(IntentPipeline.class));
                });
    }

    @Test
    void theRedisStoreUsesTheApplicationsRedisConnection() {
        REDIS.start();
        LettuceConnectionFactory connection = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connection.afterPropertiesSet();
        try {
            runner.withBean(RedisConnectionFactory.class, () -> connection)
                    .withPropertyValues("intentflow.llm.rate-limit.store=redis").run(ctx -> {
                        assertThat(ctx.getBean(LlmRateLimiter.class)).isInstanceOf(RedisRateLimiter.class);
                        secondQuestionIsLimited(ctx.getBean(IntentPipeline.class));
                    });
        } finally {
            connection.destroy();
        }
    }

    @Test
    void redisWithoutAConnectionSaysWhatToAdd() {
        runner.withPropertyValues("intentflow.llm.rate-limit.store=redis")
                .run(ctx -> assertThat(ctx).hasFailed().getFailure().rootCause()
                        .hasMessageContaining("spring-boot-starter-data-redis"));
    }

    @Test
    void aSharedStoreWithoutItsModuleFailsAtStartup() {
        runner.withClassLoader(new FilteredClassLoader("io.github.serfom256.intentflow.ratelimit.redis"))
                .withPropertyValues("intentflow.llm.rate-limit.store=redis")
                .run(ctx -> assertThat(ctx).hasFailed().getFailure().rootCause()
                        .hasMessageContaining("io.github.serfom256:intentflow-ratelimit-redis on the classpath"));
        runner.withClassLoader(new FilteredClassLoader("io.github.serfom256.intentflow.ratelimit.jdbc"))
                .withPropertyValues("intentflow.llm.rate-limit.store=jdbc")
                .run(ctx -> assertThat(ctx).hasFailed().getFailure().rootCause()
                        .hasMessageContaining("io.github.serfom256:intentflow-ratelimit-jdbc on the classpath"));
    }
}
