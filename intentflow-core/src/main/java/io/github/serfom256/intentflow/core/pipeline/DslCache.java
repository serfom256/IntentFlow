package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ast.DslSource;

import java.util.Optional;

/**
 * Caches normalized text → DSL so repeated questions skip the LLM round-trip.
 */
public interface DslCache {

    Optional<DslSource> get(DslCacheKey key);

    void put(DslCacheKey key, DslSource dsl);

    void invalidate(DslCacheKey key);

    void invalidateAll();
}
