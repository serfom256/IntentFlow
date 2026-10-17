package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.api.UserText;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import org.jspecify.annotations.Nullable;

public record DslCacheKey(UserText text, @Nullable ServiceName scope, int catalogVersion) {

    public DslCacheKey(UserText text, @Nullable ServiceName scope) {
        this(text, scope, 0);
    }
}
