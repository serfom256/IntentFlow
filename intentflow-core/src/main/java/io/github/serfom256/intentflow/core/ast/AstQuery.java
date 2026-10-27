package io.github.serfom256.intentflow.core.ast;

import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.catalog.SortKey;

import java.util.List;
import java.util.Optional;

/**
 * Parsed DSL query: service, optional filter, sort and pagination. Results are
 * always whole entities.
 */
public interface AstQuery {

    ServiceName service();

    Optional<FilterExpr> filter();

    List<SortKey> sort();

    Optional<Skip> skip();

    Optional<Take> take();

    /**
     * Grouping and aggregate function; empty when the query returns entities.
     */
    default Optional<Aggregate> aggregate() {
        return Optional.empty();
    }

    /**
     * Keeps only the first rows of each partition, as in "top 3 products per
     * category"; empty when unused.
     */
    default Optional<TopN> top() {
        return Optional.empty();
    }
}
