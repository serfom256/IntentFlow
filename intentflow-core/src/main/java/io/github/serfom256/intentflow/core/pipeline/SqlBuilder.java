package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ast.BoundQuery;

public interface SqlBuilder {

    BoundQuery build(ValidatedQuery query);
}
