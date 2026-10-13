package io.github.serfom256.intentflow.core.catalog.factories;

import io.github.serfom256.intentflow.core.catalog.ServiceDescription;
import io.github.serfom256.intentflow.core.catalog.impl.DefaultServiceDescription;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ServiceDescriptions {

    public static ServiceDescription of(String value) {
        return new DefaultServiceDescription(value);
    }
}
