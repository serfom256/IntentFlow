package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ast.AstQuery;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;

public interface AstValidator {

    ValidatedQuery validate(AstQuery ast, ServiceCatalog catalog);
}
