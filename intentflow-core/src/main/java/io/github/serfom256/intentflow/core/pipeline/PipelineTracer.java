package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.catalog.ServiceName;
import org.jspecify.annotations.Nullable;

/**
 * Opens a span around each stage of the pipeline, for distributed tracing. The
 * Spring Boot starter turns spans
 * into Micrometer observations when an {@code ObservationRegistry} is present.
 * A {@code REQUEST} span wraps a
 * {@code resolve} call; the other stages start and end inside it on the same
 * thread, so an implementation that
 * keeps the current span in a thread-local nests them. Methods must be cheap
 * and must not throw.
 *
 * <p>
 * Stages: {@code GENERATE} and {@code REPAIR} are calls to the LLM,
 * {@code VALIDATE} parses and validates the
 * query, {@code BUILD} compiles it to SQL or CQL, and {@code QUERY} runs it.
 */
public interface PipelineTracer {

    PipelineTracer NONE = stage -> (service, outcome, error) -> {
    };

    enum Stage {
        REQUEST, GENERATE, REPAIR, VALIDATE, BUILD, QUERY
    }

    Span start(Stage stage);

    /**
     * A started stage. {@code outcome} is {@code ok} or {@code error} for a stage,
     * {@code rejected} or
     * {@code declined} for a query that failed validation, and the lower-case
     * {@link PipelineListener.Outcome}
     * for a request. {@code service} is {@code null} until the query names one.
     */
    @FunctionalInterface
    interface Span {

        void end(@Nullable ServiceName service, String outcome, @Nullable Throwable error);
    }
}
