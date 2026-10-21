package io.github.serfom256.intentflow.core.pipeline.factories;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class IntentPipelines {

    public static IntentPipelineBuilder builder() {
        return new IntentPipelineBuilder();
    }
}
