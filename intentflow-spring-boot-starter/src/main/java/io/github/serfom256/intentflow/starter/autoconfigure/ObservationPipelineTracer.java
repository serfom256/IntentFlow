package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.intentflow.core.pipeline.PipelineTracer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
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
                .contextualName("intentflow " + name)
                .lowCardinalityKeyValue("stage", name)
                .start();
        Observation.Scope scope = observation.openScope();
        return (service, outcome, error) -> {
            observation.lowCardinalityKeyValue("service", service == null ? "none" : service.value())
                    .lowCardinalityKeyValue("outcome", outcome);
            if (error != null) {
                observation.error(error);
            }
            scope.close();
            observation.stop();
        };
    }
}
