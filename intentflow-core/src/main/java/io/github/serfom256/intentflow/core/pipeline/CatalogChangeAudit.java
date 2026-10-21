package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.catalog.ServiceOverride;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

public record CatalogChangeAudit(Instant at, @Nullable String user, ServiceName service, Action action,
        ServiceOverride before, ServiceOverride after) {

    public enum Action {
        UPDATED, RESET
    }
}
