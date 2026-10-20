package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.intentflow.core.pipeline.PipelineListener;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;

final class PipelineObservers {

    private PipelineObservers() {
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(SecurityContextHolder.class)
    static class SpringSecurity {

        @Bean
        @ConditionalOnMissingBean
        IntentContextProvider intentFlowContextProvider() {
            return new SpringSecurityContextProvider();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    static class Micrometer {

        @Bean
        PipelineListener intentFlowMetrics(ObjectProvider<MeterRegistry> registry) {
            return new MicrometerPipelineListener(registry);
        }
    }
}
