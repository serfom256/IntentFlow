package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.api.MicroFrontendUri;

import java.util.List;

/** Renders the service's URI template from the first result item (or without placeholders when empty). */
public interface UriBuilder {

    MicroFrontendUri build(ValidatedQuery query, List<?> items);
}
