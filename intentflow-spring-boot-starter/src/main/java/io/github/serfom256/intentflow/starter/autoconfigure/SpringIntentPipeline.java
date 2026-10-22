package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.intentflow.core.api.IntentRequest;
import io.github.serfom256.intentflow.core.api.IntentResponse;
import io.github.serfom256.intentflow.core.api.IntentTrace;
import io.github.serfom256.intentflow.core.pipeline.IntentPipeline;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;

/**
 * The {@link IntentPipeline} the starter provides, with overloads for Spring's
 * {@link ParameterizedTypeReference}, so a generic entity such as
 * {@code Event<Order>} resolves without a raw-type cast. The service is found
 * by the raw class.
 */
public interface SpringIntentPipeline extends IntentPipeline {

    @SuppressWarnings("unchecked")
    default <T> IntentResponse<T> resolve(IntentRequest request, ParameterizedTypeReference<T> resultType) {
        return (IntentResponse<T>) resolve(request, rawType(resultType));
    }

    default IntentTrace explain(IntentRequest request, ParameterizedTypeReference<?> resultType) {
        return explain(request, rawType(resultType));
    }

    /** Adds the overloads to any pipeline. */
    static SpringIntentPipeline of(IntentPipeline pipeline) {
        return pipeline instanceof SpringIntentPipeline spring ? spring : new DelegatingSpringIntentPipeline(pipeline);
    }

    /**
     * The class a {@code ParameterizedTypeReference} stands for, without its type
     * arguments.
     */
    static Class<?> rawType(ParameterizedTypeReference<?> type) {
        return ResolvableType.forType(type).toClass();
    }
}
