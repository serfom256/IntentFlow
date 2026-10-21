package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.intentflow.core.api.TokenUsage;
import io.github.serfom256.intentflow.core.api.UserText;
import io.github.serfom256.intentflow.core.api.factories.IntentRequests;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.DslSource;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.pipeline.IntentPipeline;
import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import io.github.serfom256.intentflow.core.pipeline.LlmException;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import io.github.serfom256.intentflow.starter.autoconfigure.fixtures.Book;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityAutoConfigurationTest {

    private static final String FIXTURES = "io.github.serfom256.intentflow.starter.autoconfigure.fixtures";
    private static final String HEALTH = "intentflowLlmHealthIndicator";

    private final AtomicInteger pings = new AtomicInteger();
    private volatile RuntimeException pingFailure;

    private final LlmClient llm = new LlmClient() {
        @Override
        public DslSource generateDsl(UserText normalizedText, ServiceCatalog catalog) {
            return DslSources.of("books", new TokenUsage(300, 9), "local-model");
        }

        @Override
        public void ping() {
            pings.incrementAndGet();
            if (pingFailure != null) {
                throw pingFailure;
            }
        }
    };

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IntentFlowAutoConfiguration.class))
            .withBean(LlmClient.class, () -> llm)
            .withBean(QueryExecutor.class, () -> new QueryExecutor() {
                @Override
                public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
                    return List.of();
                }
            })
            .withPropertyValues("intentflow.llm.model=m", "intentflow.catalog.packages=" + FIXTURES,
                    "intentflow.schema.introspect=false", "intentflow.dialect=postgresql");

    @Test
    void everyStageIsAnObservationInsideTheRequest() {
        List<Observation.Context> stopped = new CopyOnWriteArrayList<>();
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new ObservationHandler<>() {
            @Override
            public boolean supportsContext(Observation.Context context) {
                return true;
            }

            @Override
            public void onStop(Observation.Context context) {
                stopped.add(context);
            }
        });

        runner.withBean(ObservationRegistry.class, () -> registry).run(ctx -> {
            ctx.getBean(IntentPipeline.class).resolve(IntentRequests.of("books"), Book.class);

            assertThat(stopped).extracting(Observation.Context::getContextualName).containsExactly(
                    "intentflow generate", "intentflow validate", "intentflow build", "intentflow query",
                    "intentflow request");
            assertThat(stopped).allSatisfy(context -> {
                assertThat(context.getName()).isEqualTo("intentflow.stage");
                assertThat(tags(context)).containsKeys("stage", "service", "outcome");
            });
            Observation.Context request = stopped.getLast();
            assertThat(tags(request)).containsEntry("service", "books").containsEntry("outcome", "answered");
            assertThat(highCardinality(stopped.getFirst())).containsEntry("gen_ai.usage.input_tokens", "300")
                    .containsEntry("gen_ai.usage.output_tokens", "9")
                    .containsEntry("gen_ai.response.model", "local-model");
            assertThat(highCardinality(request)).containsEntry("gen_ai.usage.input_tokens", "300")
                    .doesNotContainKey("gen_ai.response.model");
            assertThat(tags(stopped.getFirst())).doesNotContainKey("gen_ai.usage.input_tokens");
            assertThat(stopped.subList(0, 4)).allSatisfy(context -> assertThat(
                    context.getParentObservation().getContextView()).isSameAs(request));
        });
    }

    @Test
    void theLlmIsUpWhenItAnswersAndTheResultIsReused() {
        runner.run(ctx -> {
            Object indicator = ctx.getBean(HEALTH);

            assertThat(status(indicator)).isEqualTo("UP");
            assertThat(status(indicator)).isEqualTo("UP");
            assertThat(pings).hasValue(1);
        });
    }

    @Test
    void theLlmIsDownWithTheErrorWhenItFails() {
        pingFailure = new LlmException("LLM returned HTTP 401");

        runner.withPropertyValues("intentflow.llm.health-interval=0s").run(ctx -> {
            Object indicator = ctx.getBean(HEALTH);

            assertThat(status(indicator)).isEqualTo("DOWN");
            assertThat(details(indicator)).containsEntry("error", "LLM returned HTTP 401");
            pingFailure = null;
            assertThat(status(indicator)).isEqualTo("UP");
        });
    }

    @Test
    void aClientThatCannotCheckIsUnknown() {
        runner.withBean(LlmClient.class, () -> (text, catalog) -> DslSources.of("books"))
                .withAllowBeanDefinitionOverriding(true)
                .run(ctx -> assertThat(status(ctx.getBean(HEALTH))).isEqualTo("UNKNOWN"));
    }

    @Test
    void theHealthCheckCanBeTurnedOff() {
        runner.withPropertyValues("management.health.intentflow-llm.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(HEALTH));
    }

    private static Map<String, String> highCardinality(Observation.Context context) {
        return context.getHighCardinalityKeyValues().stream()
                .collect(Collectors.toMap(KeyValue::getKey, KeyValue::getValue));
    }

    private static Map<String, String> tags(Observation.Context context) {
        return context.getLowCardinalityKeyValues().stream()
                .collect(Collectors.toMap(KeyValue::getKey, KeyValue::getValue));
    }

    private static String status(Object indicator) throws ReflectiveOperationException {
        Object status = call(health(indicator), "getStatus");
        return (String) call(status, "getCode");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> details(Object indicator) throws ReflectiveOperationException {
        return (Map<String, Object>) call(health(indicator), "getDetails");
    }

    private static Object health(Object indicator) throws ReflectiveOperationException {
        for (Class<?> type : indicator.getClass().getInterfaces()) {
            if (type.getSimpleName().equals("HealthIndicator")) {
                return type.getMethod("health").invoke(indicator);
            }
        }
        throw new AssertionError(indicator + " is not a HealthIndicator");
    }

    private static Object call(Object target, String method) throws ReflectiveOperationException {
        Method m = target.getClass().getMethod(method);
        return m.invoke(target);
    }
}
