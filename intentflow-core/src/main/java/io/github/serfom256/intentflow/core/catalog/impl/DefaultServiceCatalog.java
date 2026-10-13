package io.github.serfom256.intentflow.core.catalog.impl;

import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.ServiceName;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class DefaultServiceCatalog implements ServiceCatalog {

    private final Map<ServiceName, ServiceDescriptor> services;
    private final int version;

    public DefaultServiceCatalog(Collection<ServiceDescriptor> descriptors, int version) {
        Map<ServiceName, ServiceDescriptor> map = new LinkedHashMap<>();
        for (ServiceDescriptor d : descriptors) {
            if (map.putIfAbsent(d.name(), d) != null) {
                throw new IllegalArgumentException("duplicate service: " + d.name().value());
            }
        }
        this.services = Map.copyOf(map);
        this.version = version;
    }

    @Override
    public Optional<ServiceDescriptor> find(ServiceName name) {
        return Optional.ofNullable(services.get(name));
    }

    @Override
    public Set<ServiceName> names() {
        return services.keySet();
    }

    @Override
    public int version() {
        return version;
    }
}
