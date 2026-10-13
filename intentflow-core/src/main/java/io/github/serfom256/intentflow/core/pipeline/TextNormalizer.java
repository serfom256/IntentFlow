package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.api.UserText;

/**
 * Normalizes user-supplied natural language before it reaches the LLM cache.
 * Default implementation is provided by the starter; client apps may override
 * by registering their own {@code TextNormalizer} bean.
 */
@FunctionalInterface
public interface TextNormalizer {

    UserText normalize(UserText input);
}
