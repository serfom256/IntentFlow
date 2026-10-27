package io.github.serfom256.intentflow.core.pipeline.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.serfom256.intentflow.core.ast.DslSource;
import io.github.serfom256.intentflow.core.pipeline.DslCache;
import io.github.serfom256.intentflow.core.pipeline.DslCacheKey;

import java.time.Duration;
import java.util.Optional;

public final class DefaultDslCache implements DslCache {

    private final Cache<DslCacheKey, DslSource> cache;

    public DefaultDslCache(Duration ttl, long maxSize) {
        if (ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("DslCache ttl must be positive");
        }
        if (maxSize <= 0) {
            throw new IllegalArgumentException("DslCache maxSize must be > 0");
        }
        this.cache = Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(maxSize).build();
    }

    @Override
    public Optional<DslSource> get(DslCacheKey key) {
        return Optional.ofNullable(cache.getIfPresent(key));
    }

    @Override
    public void put(DslCacheKey key, DslSource dsl) {
        cache.put(key, dsl);
    }

    @Override
    public void invalidate(DslCacheKey key) {
        cache.invalidate(key);
    }

    @Override
    public void invalidateAll() {
        cache.invalidateAll();
    }
}
