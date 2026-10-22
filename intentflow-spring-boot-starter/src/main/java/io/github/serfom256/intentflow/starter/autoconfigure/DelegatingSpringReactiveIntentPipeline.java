package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.intentflow.core.api.IntentRequest;
import io.github.serfom256.intentflow.core.api.IntentResponse;
import io.github.serfom256.intentflow.core.api.IntentTrace;
import io.github.serfom256.intentflow.reactive.ReactiveIntentPipeline;
import reactor.core.publisher.Mono;

record DelegatingSpringReactiveIntentPipeline(ReactiveIntentPipeline pipeline) implements SpringReactiveIntentPipeline {

    @Override
    public Mono<IntentResponse<?>> resolve(IntentRequest request) {
        return pipeline.resolve(request);
    }

    @Override
    public <T> Mono<IntentResponse<T>> resolve(IntentRequest request, Class<T> resultType) {
        return pipeline.resolve(request, resultType);
    }

    @Override
    public Mono<IntentTrace> explain(IntentRequest request) {
        return pipeline.explain(request);
    }

    @Override
    public Mono<IntentTrace> explain(IntentRequest request, Class<?> resultType) {
        return pipeline.explain(request, resultType);
    }
}
