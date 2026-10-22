package io.github.serfom256.intentflow.reactive;

import io.github.serfom256.intentflow.core.pipeline.IntentPipeline;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ReactiveIntentPipelines {

    public static ReactiveIntentPipeline of(IntentPipeline pipeline) {
        return of(pipeline, Schedulers.boundedElastic(), ReactiveIntentContextProvider.NONE);
    }

    public static ReactiveIntentPipeline of(IntentPipeline pipeline, Scheduler scheduler,
            ReactiveIntentContextProvider callers) {
        return new BlockingReactiveIntentPipeline(pipeline, scheduler, callers);
    }
}
