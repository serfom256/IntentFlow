package io.github.serfom256.intentflow.core.catalog;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Read-only view of the service registry loaded at application start.
 * The {@link #version()} should increment whenever the catalog is rebuilt
 * so dependent caches (e.g. NL → DSL) can scope their keys.
 */
public interface ServiceCatalog {

    Optional<ServiceDescriptor> find(ServiceName name);

    Set<ServiceName> names();

    int version();

    /**
     * The service whose {@link ServiceDescriptor#resultType()} is exactly {@code type}.
     *
     * @throws IllegalStateException when several services share that type (resolve by name instead)
     */
    default Optional<ServiceDescriptor> findByType(Class<?> type) {
        List<ServiceDescriptor> matches = names().stream().map(this::get)
                .filter(d -> d.resultType() == type).toList();
        if (matches.size() > 1) {
            throw new IllegalStateException("Several services map to " + type.getName() + ": "
                    + matches.stream().map(d -> d.name().value()).sorted().toList());
        }
        return matches.stream().findFirst();
    }

    default ServiceDescriptor get(ServiceName name) {
        return find(name).orElseThrow(() -> new ServiceNotFoundException(name));
    }
}
