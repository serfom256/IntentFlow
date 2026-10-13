package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ast.AstQuery;
import io.github.serfom256.intentflow.core.ast.DslSource;

public interface DslParser {

    AstQuery parse(DslSource dsl);
}
