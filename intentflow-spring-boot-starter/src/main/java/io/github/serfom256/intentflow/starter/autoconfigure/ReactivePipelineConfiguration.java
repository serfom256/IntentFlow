package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.intentflow.core.pipeline.IntentPipeline;
import io.github.serfom256.intentflow.reactive.ReactiveIntentContextProvider;
import io.github.serfom256.intentflow.reactive.ReactiveIntentPipeline;
import io.github.serfom256.intentflow.reactive.ReactiveIntentPipelines;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.scheduler.Schedulers;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = { "reactor.core.publisher.Mono",
        "io.github.serfom256.intentflow.reactive.ReactiveIntentPipeline" })
class ReactivePipelineConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ReactiveIntentPipeline intentFlowReactivePipeline(IntentPipeline pipeline,
            ObjectProvider<ReactiveIntentContextProvider> callers) {
        return ReactiveIntentPipelines.of(pipeline, Schedulers.boundedElastic(),
                callers.getIfAvailable(() -> ReactiveIntentContextProvider.NONE));
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.security.core.context.ReactiveSecurityContextHolder")
    static class SpringSecurity {

        @Bean
        @ConditionalOnMissingBean
        ReactiveIntentContextProvider intentFlowReactiveContextProvider() {
            return () -> ReactiveSecurityContextHolder.getContext()
                    .map(security -> SpringSecurityContextProvider.from(security.getAuthentication()));
        }
    }
}
