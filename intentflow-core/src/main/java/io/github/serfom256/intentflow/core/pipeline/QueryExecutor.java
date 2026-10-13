package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ast.BoundQuery;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Runs generated queries against the application's data access: plain JDBC
 * ({@code intentflow-jdbc}), Spring Data JDBC, JPA, OpenMapper or Cassandra, or
 * any implementation plugged in.
 */
public interface QueryExecutor {

    /** Runs an entity query ({@code SELECT *}) and maps every row onto {@code resultType}. */
    <T> List<T> execute(BoundQuery query, Class<T> resultType);

    /**
     * Runs an aggregate query and returns its columns as plain values, in select order: the group
     * fields, then the aggregated value.
     */
    default List<List<@Nullable Object>> rows(BoundQuery query) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not run aggregate queries");
    }
}
