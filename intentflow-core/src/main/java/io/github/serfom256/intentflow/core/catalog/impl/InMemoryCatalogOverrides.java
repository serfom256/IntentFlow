package io.github.serfom256.intentflow.core.catalog.impl;

import io.github.serfom256.intentflow.core.catalog.CatalogOverrides;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.catalog.ServiceOverride;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryCatalogOverrides implements CatalogOverrides {

    private final Map<ServiceName, ServiceOverride> overrides = new ConcurrentHashMap<>();
    private final AtomicLong version = new AtomicLong();

    @Override
    public Optional<ServiceOverride> find(ServiceName service) {
        return Optional.ofNullable(overrides.get(service));
    }

    @Override
    public Map<ServiceName, ServiceOverride> all() {
        return Map.copyOf(overrides);
    }

    @Override
    public void put(ServiceName service, ServiceOverride override) {
        if (override.isEmpty()) {
            remove(service);
            return;
        }
        overrides.put(service, override);
        version.incrementAndGet();
    }

    @Override
    public void remove(ServiceName service) {
        if (overrides.remove(service) != null) {
            version.incrementAndGet();
        }
    }

    @Override
    public long version() {
        return version.get();
    }
}
