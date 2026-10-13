package io.github.serfom256.intentflow.core.catalog.factories;

import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.impl.DefaultServiceCatalog;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.Collection;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ServiceCatalogs {

    public static ServiceCatalog of(Collection<ServiceDescriptor> descriptors) {
        return new DefaultServiceCatalog(descriptors, 1);
    }

    public static ServiceCatalog of(ServiceDescriptor... descriptors) {
        return of(Arrays.asList(descriptors));
    }

    public static ServiceCatalog fromAnnotated(Class<?>... types) {
        return of(Arrays.stream(types).map(ServiceDescriptors::fromAnnotated).toList());
    }
}
