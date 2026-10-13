package io.github.serfom256.intentflow.core.catalog.factories;

import io.github.serfom256.intentflow.core.catalog.ServiceRelation;
import io.github.serfom256.intentflow.core.catalog.impl.DefaultServiceRelation;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ServiceRelations {

    public static ServiceRelation of(String name, String target, String from, String to) {
        return new DefaultServiceRelation(name, ServiceNames.of(target), LogicalFieldNames.of(from),
                LogicalFieldNames.of(to));
    }
}
