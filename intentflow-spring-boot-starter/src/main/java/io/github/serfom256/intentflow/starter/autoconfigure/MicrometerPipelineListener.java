package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.intentflow.core.api.TokenUsage;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.pipeline.PipelineListener;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.util.Locale;

final class MicrometerPipelineListener implements PipelineListener {

    private final ObjectProvider<MeterRegistry> registry;

    MicrometerPipelineListener(ObjectProvider<MeterRegistry> registry) {
        this.registry = registry;
    }

    @Override
    public void cache(boolean hit) {
        MeterRegistry meters = registry.getIfAvailable();
        if (meters != null) {
            Counter.builder("intentflow.cache").description("DSL cache lookups")
                    .tag("result", hit ? "hit" : "miss").register(meters).increment();
        }
    }

    @Override
    public void llm(boolean repair, Duration elapsed, boolean failed) {
        MeterRegistry meters = registry.getIfAvailable();
        if (meters != null) {
            Timer.builder("intentflow.llm").description("Calls to the LLM")
                    .tag("kind", repair ? "repair" : "generate").tag("outcome", failed ? "error" : "ok")
                    .register(meters).record(elapsed);
        }
    }

    @Override
    public void tokens(@Nullable String model, boolean repair, TokenUsage usage) {
        MeterRegistry meters = registry.getIfAvailable();
        if (meters != null) {
            String kind = repair ? "repair" : "generate";
            String name = model == null ? "unknown" : model;
            tokenCounter(meters, name, kind, "prompt").increment(usage.promptTokens());
            tokenCounter(meters, name, kind, "completion").increment(usage.completionTokens());
        }
    }

    private static Counter tokenCounter(MeterRegistry meters, String model, String kind, String type) {
        return Counter.builder("intentflow.llm.tokens").description("Tokens the LLM used").baseUnit("tokens")
                .tag("model", model).tag("kind", kind).tag("type", type).register(meters);
    }

    @Override
    public void query(ServiceName service, boolean aggregate, Duration elapsed, int rows, boolean failed) {
        MeterRegistry meters = registry.getIfAvailable();
        if (meters != null) {
            String kind = aggregate ? "aggregate" : "rows";
            Timer.builder("intentflow.query").description("Generated queries run by the executor")
                    .tag("service", service.value()).tag("kind", kind).tag("outcome", failed ? "error" : "ok")
                    .register(meters).record(elapsed);
            if (!failed) {
                DistributionSummary.builder("intentflow.query.rows")
                        .description("Entities or aggregate groups a query returned")
                        .tag("service", service.value()).tag("kind", kind).register(meters).record(rows);
            }
        }
    }

    @Override
    public void request(@Nullable ServiceName service, Outcome outcome, Duration elapsed) {
        MeterRegistry meters = registry.getIfAvailable();
        if (meters != null) {
            Timer.builder("intentflow.requests").description("Questions answered by the pipeline")
                    .tag("service", service == null ? "none" : service.value())
                    .tag("outcome", outcome.name().toLowerCase(Locale.ROOT)).register(meters).record(elapsed);
        }
    }
}
