package io.github.serfom256.intentflow.core.ast;

import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** Parsed DSL query: service, optional filter, sort and pagination. Results are always whole entities. */
public interface AstQuery {

    ServiceName service();

    @Nullable
    FilterExpr filter();

    List<SortKey> sort();

    @Nullable
    Skip skip();

    @Nullable
    Take take();

    /** Grouping and aggregate function; {@code null} when the query returns entities. */
    default @Nullable Aggregate aggregate() {
        return null;
    }
}
