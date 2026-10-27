package io.github.serfom256.intentflow.reactive;

import io.github.serfom256.intentflow.core.api.IntentRequest;
import io.github.serfom256.intentflow.core.api.IntentResponse;
import io.github.serfom256.intentflow.core.api.IntentTrace;
import reactor.core.publisher.Mono;

/**
 * {@code IntentPipeline} for reactive applications such as Spring WebFlux. The
 * LLM call and the query block, so each call runs the pipeline on a scheduler
 * meant for blocking work and never on the caller's event loop. A request
 * without a caller takes it from the subscriber's Reactor context through a
 * {@link ReactiveIntentContextProvider}. Errors arrive as the same exceptions
 * {@code IntentPipeline} throws.
 */
public interface ReactiveIntentPipeline {

    Mono<IntentResponse<?>> resolve(IntentRequest request);

    <T> Mono<IntentResponse<T>> resolve(IntentRequest request, Class<T> resultType);

    Mono<IntentTrace> explain(IntentRequest request);

    Mono<IntentTrace> explain(IntentRequest request, Class<?> resultType);
}
