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

    /**
     * Runs an entity query ({@code SELECT *}) and maps every row onto
     * {@code resultType}.
     */
    <T> List<T> execute(BoundQuery query, Class<T> resultType);

    /**
     * What an entity query for {@code resultType} has to select: every column
     * ({@link ResultShape#ROWS}, the
     * default), or only the primary key when the executor loads the entities itself
     * ({@link ResultShape#KEYS}).
     * A query selects every column anyway when the key is not known.
     */
    default ResultShape shape(Class<?> resultType) {
        return ResultShape.ROWS;
    }

    /**
     * Like {@link #execute(BoundQuery, Class)}, for executors that also load
     * relations of the result: they run the
     * statements {@code related} builds. The default ignores {@code related}.
     */
    default <T> List<T> execute(BoundQuery query, Class<T> resultType, RelatedRows related) {
        return execute(query, resultType);
    }

    /**
     * Conditions that rows of {@code entityType} must meet in the session this
     * executor runs in, such as the
     * Hibernate filters enabled on the current session. They are added to every
     * query of that entity, including
     * relation subqueries. The default adds none.
     */
    default List<RuntimeRestriction> restrictions(Class<?> entityType) {
        return List.of();
    }

    /**
     * Runs an aggregate query and returns its columns as plain values, in select
     * order: the group
     * fields, then the aggregated value.
     */
    default List<List<@Nullable Object>> rows(BoundQuery query) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not run aggregate queries");
    }
}
