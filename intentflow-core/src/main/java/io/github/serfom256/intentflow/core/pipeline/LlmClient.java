package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.api.UserText;
import io.github.serfom256.intentflow.core.ast.DslSource;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;

/**
 * Translates normalized natural-language text into a DSL string. The catalog
 * is passed so the implementation can build a prompt that lists available
 * service names, their descriptions, and queryable fields.
 */
public interface LlmClient {

    DslSource generateDsl(UserText normalizedText, ServiceCatalog catalog);
}
