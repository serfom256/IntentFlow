package io.github.serfom256.intentflow.reactive;

import io.github.serfom256.intentflow.core.api.IntentRequest;
import io.github.serfom256.intentflow.core.api.IntentResponse;
import io.github.serfom256.intentflow.core.api.IntentTrace;
import io.github.serfom256.intentflow.core.api.factories.IntentContexts;
import io.github.serfom256.intentflow.core.api.factories.IntentRequests;
import io.github.serfom256.intentflow.core.pipeline.IntentPipeline;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.util.function.Function;

final class BlockingReactiveIntentPipeline implements ReactiveIntentPipeline {

    private final IntentPipeline pipeline;
    private final Scheduler scheduler;
    private final ReactiveIntentContextProvider callers;

    BlockingReactiveIntentPipeline(IntentPipeline pipeline, Scheduler scheduler,
            ReactiveIntentContextProvider callers) {
        this.pipeline = pipeline;
        this.scheduler = scheduler;
        this.callers = callers;
    }

    @Override
    public Mono<IntentResponse<?>> resolve(IntentRequest request) {
        return run(request, pipeline::resolve);
    }

    @Override
    public <T> Mono<IntentResponse<T>> resolve(IntentRequest request, Class<T> resultType) {
        return run(request, withCaller -> pipeline.resolve(withCaller, resultType));
    }

    @Override
    public Mono<IntentTrace> explain(IntentRequest request) {
        return run(request, pipeline::explain);
    }

    @Override
    public Mono<IntentTrace> explain(IntentRequest request, Class<?> resultType) {
        return run(request, withCaller -> pipeline.explain(withCaller, resultType));
    }

    private <R> Mono<R> run(IntentRequest request, Function<IntentRequest, R> call) {
        Mono<IntentRequest> withCaller = request.context() != null ? Mono.just(request)
                : callers.current().defaultIfEmpty(IntentContexts.anonymous())
                        .map(caller -> IntentRequests.of(request.text(), caller));
        return withCaller.publishOn(scheduler).map(call);
    }
}
