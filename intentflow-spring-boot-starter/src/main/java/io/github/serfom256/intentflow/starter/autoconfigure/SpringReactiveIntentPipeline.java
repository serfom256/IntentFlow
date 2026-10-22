package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.intentflow.core.api.IntentRequest;
import io.github.serfom256.intentflow.core.api.IntentResponse;
import io.github.serfom256.intentflow.core.api.IntentTrace;
import io.github.serfom256.intentflow.reactive.ReactiveIntentPipeline;
import org.springframework.core.ParameterizedTypeReference;
import reactor.core.publisher.Mono;

/**
 * The {@link ReactiveIntentPipeline} the starter provides, with the
 * {@link ParameterizedTypeReference} overloads of {@link SpringIntentPipeline}.
 */
public interface SpringReactiveIntentPipeline extends ReactiveIntentPipeline {

    @SuppressWarnings("unchecked")
    default <T> Mono<IntentResponse<T>> resolve(IntentRequest request, ParameterizedTypeReference<T> resultType) {
        return resolve(request, SpringIntentPipeline.rawType(resultType)).map(response -> (IntentResponse<T>) response);
    }

    default Mono<IntentTrace> explain(IntentRequest request, ParameterizedTypeReference<?> resultType) {
        return explain(request, SpringIntentPipeline.rawType(resultType));
    }

    /** Adds the overloads to any reactive pipeline. */
    static SpringReactiveIntentPipeline of(ReactiveIntentPipeline pipeline) {
        return pipeline instanceof SpringReactiveIntentPipeline spring ? spring
                : new DelegatingSpringReactiveIntentPipeline(pipeline);
    }
}
