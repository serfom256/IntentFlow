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

    /**
     * Asks for a corrected query after {@code failed} was rejected with
     * {@code error}, e.g. because it used an
     * unknown field or a function the wrong way. The default asks again from
     * scratch; clients that keep a
     * conversation should show the model its previous answer and the error.
     */
    default DslSource repairDsl(UserText normalizedText, ServiceCatalog catalog, DslSource failed, String error) {
        return generateDsl(normalizedText, catalog);
    }

    /**
     * Checks that the model endpoint answers and accepts the credentials, without
     * generating text, for a health
     * check. Throws when it does not. The default throws
     * {@link UnsupportedOperationException}, which a health
     * check reports as unknown.
     */
    default void ping() {
        throw new UnsupportedOperationException(getClass().getName() + " cannot check its endpoint");
    }
}
