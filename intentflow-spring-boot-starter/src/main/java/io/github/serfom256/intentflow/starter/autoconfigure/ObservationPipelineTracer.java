package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.intentflow.core.api.TokenUsage;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.pipeline.PipelineTracer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Locale;

final class ObservationPipelineTracer implements PipelineTracer {

    private final ObjectProvider<ObservationRegistry> registry;

    ObservationPipelineTracer(ObjectProvider<ObservationRegistry> registry) {
        this.registry = registry;
    }

    @Override
    public Span start(Stage stage) {
        ObservationRegistry observations = registry.getIfAvailable();
        if (observations == null || observations.isNoop()) {
            return NONE.start(stage);
        }
        String name = stage.name().toLowerCase(Locale.ROOT);
        Observation observation = Observation.createNotStarted("intentflow.stage", observations)
                .contextualName("intentflow " + name).lowCardinalityKeyValue("stage", name).start();
        return new ObservedSpan(observation, observation.openScope());
    }

    private record ObservedSpan(Observation observation, Observation.Scope scope) implements Span {

        @Override
        public void usage(TokenUsage tokens, @Nullable String model) {
            observation.highCardinalityKeyValue("gen_ai.usage.input_tokens", String.valueOf(tokens.promptTokens()))
                    .highCardinalityKeyValue("gen_ai.usage.output_tokens", String.valueOf(tokens.completionTokens()));
            if (model != null) {
                observation.highCardinalityKeyValue("gen_ai.response.model", model);
            }
        }

        @Override
        public void end(@Nullable ServiceName service, String outcome, @Nullable Throwable error) {
            observation.lowCardinalityKeyValue("service", service == null ? "none" : service.value())
                    .lowCardinalityKeyValue("outcome", outcome);
            if (error != null) {
                observation.error(error);
            }
            scope.close();
            observation.stop();
        }
    }
}
