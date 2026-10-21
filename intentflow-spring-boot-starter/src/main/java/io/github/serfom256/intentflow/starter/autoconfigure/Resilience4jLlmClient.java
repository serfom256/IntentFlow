package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.serfom256.intentflow.core.api.UserText;
import io.github.serfom256.intentflow.core.ast.DslSource;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import io.github.serfom256.intentflow.core.pipeline.LlmException;
import io.github.serfom256.intentflow.core.pipeline.LlmUnavailableException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Supplier;

final class Resilience4jLlmClient implements LlmClient {

    private final LlmClient delegate;
    private final CircuitBreaker breaker;

    Resilience4jLlmClient(LlmClient delegate, CircuitBreaker breaker) {
        this.delegate = delegate;
        this.breaker = breaker;
    }

    @Override
    public DslSource generateDsl(UserText normalizedText, ServiceCatalog catalog) {
        return call(() -> delegate.generateDsl(normalizedText, catalog));
    }

    @Override
    public DslSource repairDsl(UserText normalizedText, ServiceCatalog catalog, DslSource failed, String error) {
        return call(() -> delegate.repairDsl(normalizedText, catalog, failed, error));
    }

    @Override
    public void ping() {
        delegate.ping();
    }

    private DslSource call(Supplier<DslSource> work) {
        try {
            return breaker.executeSupplier(work);
        } catch (CallNotPermittedException e) {
            throw new LlmUnavailableException("LLM circuit '" + breaker.getName() + "' is open", null);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry")
    static class Wiring {

        @Bean
        LlmCircuitBreaker intentFlowLlmCircuitBreaker(ObjectProvider<CircuitBreakerRegistry> registries,
                IntentFlowProperties properties) {
            return (client, name) -> {
                CircuitBreakerRegistry registry = registries.getIfAvailable();
                if (registry == null) {
                    return IntentFlowAutoConfiguration.builtInBreaker(client, properties.llm());
                }
                return new Resilience4jLlmClient(client, registry.circuitBreaker(name,
                        () -> CircuitBreakerConfig.from(registry.getDefaultConfig())
                                .recordExceptions(LlmException.class)
                                .ignoreExceptions(LlmUnavailableException.class)
                                .build()));
            };
        }
    }
}
