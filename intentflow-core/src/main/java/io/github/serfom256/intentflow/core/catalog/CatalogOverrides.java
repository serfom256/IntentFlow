package io.github.serfom256.intentflow.core.catalog;

import java.util.Map;
import java.util.Optional;

/**
 * Runtime changes to the declared catalog: service descriptions, field
 * descriptions and URI templates that replace
 * what annotations or builders declared, e.g. edited in the admin UI.
 * Implementations decide where they are kept
 * (memory, a database table) and must be safe for concurrent use.
 */
public interface CatalogOverrides {

    /** The override of one service, if any. */
    Optional<ServiceOverride> find(ServiceName service);

    /** Every stored override. */
    Map<ServiceName, ServiceOverride> all();

    /** Replaces the override of {@code service}; an empty override removes it. */
    void put(ServiceName service, ServiceOverride override);

    /** Removes the override of {@code service}, restoring the declared values. */
    void remove(ServiceName service);

    /**
     * Changes whenever the stored overrides change, so caches built on the catalog
     * can tell they are stale.
     */
    long version();
}
