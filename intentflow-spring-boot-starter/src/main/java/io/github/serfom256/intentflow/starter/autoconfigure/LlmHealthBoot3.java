package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
@ConditionalOnProperty(prefix = "management.health.intentflow-llm", name = "enabled", matchIfMissing = true)
class LlmHealthBoot3 {

    @Bean
    HealthIndicator intentflowLlmHealthIndicator(LlmClient llm, IntentFlowProperties properties) {
        LlmHealth health = new LlmHealth(llm, properties.llm().healthInterval());
        return () -> {
            LlmHealth.Result result = health.check();
            Health.Builder builder = switch (result.status()) {
                case UP -> Health.up();
                case DOWN -> Health.down();
                case UNKNOWN -> Health.unknown();
            };
            if (result.error() != null) {
                builder.withDetail("error", result.error());
            }
            return builder.build();
        };
    }
}
