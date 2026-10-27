package io.github.serfom256.intentflow.core.api;

import io.github.serfom256.intentflow.core.catalog.ServiceName;

import java.util.List;
import java.util.Optional;

/**
 * The answer to an {@link IntentRequest}: the entities that match, mapped by
 * the configured {@code QueryExecutor}, and where they live.
 *
 * @param <T> the service's result type, e.g. {@code Product}
 */
public interface IntentResponse<T> {

    /** Service the request was routed to, e.g. {@code products}. */
    ServiceName service();

    /** Entity class of {@link #items()}. */
    Class<T> type();

    /**
     * Matching entities, in query order. Empty when nothing matched or the query
     * aggregated.
     */
    List<T> items();

    /**
     * Grouped or aggregated values when the query used {@code count}, {@code sum}
     * and so on.
     */
    Optional<Aggregation> aggregation();

    /** The DSL and statement behind this response. */
    IntentTrace trace();

    /**
     * The first match ({@code /products/12}), or the collection ({@code /products})
     * when nothing matched.
     */
    MicroFrontendUri uri();

    /**
     * Where the next page starts, for a {@link IntentRequest#paged() paged} request
     * whose query found more rows; empty on the last page and for requests that are
     * not paged. Opaque: pass it back with the same question.
     */
    default Optional<String> nextCursor() {
        return Optional.empty();
    }
}
