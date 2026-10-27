package io.github.serfom256.intentflow.starter.autoconfigure;

import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.serfom256.intentflow.core.api.factories.UserTexts;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import io.github.serfom256.intentflow.core.pipeline.LlmUnavailableException;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import io.github.serfom256.intentflow.core.pipeline.impl.CircuitBreakerLlmClient;
import io.github.serfom256.intentflow.core.pipeline.impl.ConcurrencyLimitedLlmClient;
import io.github.serfom256.intentflow.llm.openai.OpenAiCompatibleLlmClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmResilienceAutoConfigurationTest {

    private static final String FIXTURES = "io.github.serfom256.intentflow.starter.autoconfigure.fixtures";
    private static final String ANSWER = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"books\"}}]}";

    private final List<String> primaryCalls = new CopyOnWriteArrayList<>();
    private final List<String> fallbackCalls = new CopyOnWriteArrayList<>();
    private final HttpServer primary = server(503, primaryCalls);
    private final HttpServer fallback = server(200, fallbackCalls);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IntentFlowAutoConfiguration.class))
            .withBean(QueryExecutor.class, () -> new QueryExecutor() {
                @Override
                public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
                    return List.of();
                }
            }).withPropertyValues("intentflow.llm.model=big", "intentflow.llm.max-retries=0",
                    "intentflow.llm.base-url=" + url(primary), "intentflow.catalog.packages=" + FIXTURES,
                    "intentflow.schema.introspect=false", "intentflow.dialect=postgresql");

    @AfterEach
    void stop() {
        primary.stop(0);
        fallback.stop(0);
    }

    @Test
    void aFailingPrimaryOpensItsCircuitAndTheFallbackModelAnswers() {
        runner.withPropertyValues("intentflow.llm.fallback.base-url=" + url(fallback),
                "intentflow.llm.fallback.model=small", "intentflow.llm.circuit-breaker.failure-threshold=1")
                .run(ctx -> {
                    LlmClient llm = ctx.getBean(LlmClient.class);
                    ServiceCatalog catalog = ctx.getBean(ServiceCatalog.class);

                    assertThat(llm.generateDsl(UserTexts.of("books"), catalog).value()).isEqualTo("books");
                    assertThat(llm.generateDsl(UserTexts.of("books"), catalog).value()).isEqualTo("books");

                    assertThat(primaryCalls).hasSize(1);
                    assertThat(fallbackCalls).hasSize(2)
                            .allSatisfy(body -> assertThat(body).contains("\"model\":\"small\""));
                });
    }

    @Test
    void aResilience4jRegistryProvidesTheBreakers() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom().slidingWindowSize(2)
                .minimumNumberOfCalls(2).failureRateThreshold(50).build());

        runner.withBean(CircuitBreakerRegistry.class, () -> registry).withPropertyValues(
                "intentflow.llm.fallback.base-url=" + url(fallback), "intentflow.llm.fallback.model=small").run(ctx -> {
                    LlmClient llm = ctx.getBean(LlmClient.class);
                    ServiceCatalog catalog = ctx.getBean(ServiceCatalog.class);

                    for (int i = 0; i < 3; i++) {
                        assertThat(llm.generateDsl(UserTexts.of("books"), catalog).value()).isEqualTo("books");
                    }

                    assertThat(registry.circuitBreaker("intentflow-llm").getState())
                            .isEqualTo(CircuitBreaker.State.OPEN);
                    assertThat(registry.circuitBreaker("intentflow-llm-fallback").getState())
                            .isEqualTo(CircuitBreaker.State.CLOSED);
                    assertThat(primaryCalls).hasSize(2);
                    assertThat(fallbackCalls).hasSize(3);
                });
    }

    @Test
    void anOpenResilience4jCircuitIsUnavailable() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry
                .of(CircuitBreakerConfig.custom().slidingWindowSize(1).minimumNumberOfCalls(1).build());

        runner.withBean(CircuitBreakerRegistry.class, () -> registry).run(ctx -> {
            LlmClient llm = ctx.getBean(LlmClient.class);
            ServiceCatalog catalog = ctx.getBean(ServiceCatalog.class);

            assertThatThrownBy(() -> llm.generateDsl(UserTexts.of("books"), catalog))
                    .hasMessage("LLM returned HTTP 503");
            assertThatThrownBy(() -> llm.generateDsl(UserTexts.of("books"), catalog))
                    .isInstanceOf(LlmUnavailableException.class).hasMessage("LLM circuit 'intentflow-llm' is open");
            assertThat(primaryCalls).hasSize(1);
        });
    }

    @Test
    void withoutResilience4jTheBuiltInBreakerIsUsed() {
        runner.withClassLoader(new FilteredClassLoader(CircuitBreakerRegistry.class))
                .run(ctx -> assertThat(ctx.getBean(LlmClient.class)).isInstanceOf(CircuitBreakerLlmClient.class));
    }

    @Test
    void withoutAProviderModuleStartupNamesWhatToAdd() {
        runner.withClassLoader(new FilteredClassLoader(OpenAiCompatibleLlmClient.class))
                .run(ctx -> assertThat(ctx).hasFailed().getFailure()
                        .hasRootCauseMessage("No LLM provider for intentflow.llm.provider=openai: add a module from "
                                + "intentflow-providers (intentflow-openai for OpenAI-compatible APIs) or define an "
                                + "LlmClient bean."));
    }

    @Test
    void anUnknownProviderIsReported() {
        runner.withPropertyValues("intentflow.llm.provider=watsonx").run(ctx -> assertThat(ctx).hasFailed().getFailure()
                .rootCause().hasMessageContaining("intentflow.llm.provider=watsonx"));
    }

    @Test
    void theCircuitBreakerIsOnByDefault() {
        runner.run(ctx -> assertThat(ctx.getBean(LlmClient.class)).isInstanceOf(CircuitBreakerLlmClient.class));
    }

    @Test
    void aConcurrencyLimitCanBeSetAndTheBreakerTurnedOff() {
        runner.withPropertyValues("intentflow.llm.concurrency.max-calls=4",
                "intentflow.llm.circuit-breaker.failure-threshold=0")
                .run(ctx -> assertThat(ctx.getBean(LlmClient.class)).isInstanceOf(ConcurrencyLimitedLlmClient.class));
    }

    @Test
    void withEverythingOffTheClientIsThePlainOne() {
        runner.withPropertyValues("intentflow.llm.circuit-breaker.failure-threshold=0")
                .run(ctx -> assertThat(ctx.getBean(LlmClient.class)).isInstanceOf(OpenAiCompatibleLlmClient.class));
    }

    private static HttpServer server(int status, List<String> calls) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/chat/completions", exchange -> {
                calls.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] body = (status == 200 ? ANSWER : "{}").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String url(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }
}
