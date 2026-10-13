package io.github.serfom256.intentflow.core.pipeline.factories;

import io.github.serfom256.intentflow.core.pipeline.DslCache;
import io.github.serfom256.intentflow.core.pipeline.impl.DefaultDslCache;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Duration;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DslCaches {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
    private static final long DEFAULT_MAX_SIZE = 10_000L;

    public static DslCache create() {
        return new DefaultDslCache(DEFAULT_TTL, DEFAULT_MAX_SIZE);
    }

    public static DslCache create(Duration ttl, long maxSize) {
        return new DefaultDslCache(ttl, maxSize);
    }
}
