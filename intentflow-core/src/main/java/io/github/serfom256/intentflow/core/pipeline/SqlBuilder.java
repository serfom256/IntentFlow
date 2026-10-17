package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ast.BoundQuery;

public interface SqlBuilder {

    BoundQuery build(ValidatedQuery query);

    /** Builds the statement for an executor that needs the given result shape. */
    default BoundQuery build(ValidatedQuery query, ResultShape shape) {
        return build(query);
    }
}
