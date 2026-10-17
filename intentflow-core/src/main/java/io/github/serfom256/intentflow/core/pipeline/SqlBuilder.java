package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;

import java.util.List;
import java.util.function.Function;

public interface SqlBuilder {

    BoundQuery build(ValidatedQuery query);

    /** Builds the statement for an executor that needs the given result shape. */
    default BoundQuery build(ValidatedQuery query, ResultShape shape) {
        return build(query);
    }

    /**
     * Builds the statement and adds the {@code restrictions} the executor reports
     * for each entity it touches.
     * The default ignores them, for builders whose store has no such conditions.
     */
    default BoundQuery build(ValidatedQuery query, ResultShape shape,
            Function<Class<?>, List<RuntimeRestriction>> restrictions) {
        return build(query, shape);
    }

    /**
     * Statements selecting all rows of {@code target} whose {@code column} is one
     * of {@code keys}, with the
     * target's restrictions, split to stay within the database's limits.
     */
    default List<BoundQuery> buildRelated(ServiceDescriptor target, String column, List<?> keys,
            Function<Class<?>, List<RuntimeRestriction>> restrictions) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not load relations");
    }
}
