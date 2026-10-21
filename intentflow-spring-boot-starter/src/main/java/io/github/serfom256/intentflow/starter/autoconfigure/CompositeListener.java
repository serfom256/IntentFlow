package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.intentflow.core.api.TokenUsage;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.pipeline.PipelineListener;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
final class CompositeListener implements PipelineListener {

    private final List<PipelineListener> listeners;

    CompositeListener(List<PipelineListener> listeners) {
        this.listeners = List.copyOf(listeners);
    }

    @Override
    public void cache(boolean hit) {
        each(l -> l.cache(hit));
    }

    @Override
    public void llm(boolean repair, Duration elapsed, boolean failed) {
        each(l -> l.llm(repair, elapsed, failed));
    }

    @Override
    public void tokens(@Nullable String model, boolean repair, TokenUsage usage) {
        each(l -> l.tokens(model, repair, usage));
    }

    @Override
    public void query(ServiceName service, boolean aggregate, Duration elapsed, int rows, boolean failed) {
        each(l -> l.query(service, aggregate, elapsed, rows, failed));
    }

    @Override
    public void request(@Nullable ServiceName service, Outcome outcome, Duration elapsed) {
        each(l -> l.request(service, outcome, elapsed));
    }

    private void each(Consumer<PipelineListener> event) {
        for (PipelineListener listener : listeners) {
            try {
                event.accept(listener);
            } catch (RuntimeException e) {
                log.warn("IntentFlow pipeline listener {} failed", listener.getClass().getName(), e);
            }
        }
    }
}
