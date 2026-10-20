package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.catalog.ServiceName;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * Hears what the pipeline does, for metrics and monitoring. The Spring Boot
 * starter records these as Micrometer
 * meters when a {@code MeterRegistry} is present. Methods run on the request
 * thread, so they must be cheap and
 * must not throw.
 *
 * <p>
 * A request ends {@code ANSWERED} when rows or an aggregate came back,
 * {@code DECLINED} when the LLM said the
 * catalog cannot answer it, {@code REJECTED} when the query did not parse or
 * validate after the allowed repairs,
 * {@code DENIED} when the caller may not query the service, and {@code FAILED}
 * when the LLM or the database
 * failed.
 */
public interface PipelineListener {

    PipelineListener NONE = new PipelineListener() {
    };

    enum Outcome {
        ANSWERED, DECLINED, REJECTED, DENIED, FAILED
    }

    /** A translation was looked up in the DSL cache. */
    default void cache(boolean hit) {
    }

    /**
     * The LLM was asked for a query, or for a corrected one when {@code repair} is
     * set.
     */
    default void llm(boolean repair, Duration elapsed, boolean failed) {
    }

    /**
     * A generated query ran; {@code rows} is the number of entities or aggregate
     * groups.
     */
    default void query(ServiceName service, boolean aggregate, Duration elapsed, int rows, boolean failed) {
    }

    /**
     * A {@code resolve} call ended; {@code service} is {@code null} when no service
     * was chosen.
     */
    default void request(@Nullable ServiceName service, Outcome outcome, Duration elapsed) {
    }
}
