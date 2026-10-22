package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.intentflow.core.api.IntentRequest;
import io.github.serfom256.intentflow.core.api.IntentResponse;
import io.github.serfom256.intentflow.core.api.IntentTrace;
import io.github.serfom256.intentflow.core.pipeline.IntentPipeline;

record DelegatingSpringIntentPipeline(IntentPipeline pipeline) implements SpringIntentPipeline {

    @Override
    public IntentResponse<?> resolve(IntentRequest request) {
        return pipeline.resolve(request);
    }

    @Override
    public <T> IntentResponse<T> resolve(IntentRequest request, Class<T> resultType) {
        return pipeline.resolve(request, resultType);
    }

    @Override
    public IntentTrace explain(IntentRequest request) {
        return pipeline.explain(request);
    }

    @Override
    public IntentTrace explain(IntentRequest request, Class<?> resultType) {
        return pipeline.explain(request, resultType);
    }
}
