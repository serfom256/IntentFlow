package io.github.serfom256.intentflow.core.catalog.impl;

import io.github.serfom256.intentflow.core.catalog.LogicalFieldName;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.catalog.ServiceRelation;

public record DefaultServiceRelation(String name, ServiceName target, LogicalFieldName from, LogicalFieldName to)
        implements ServiceRelation {

    public DefaultServiceRelation {
        if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("relation name must be an identifier: '" + name + "'");
        }
    }
}
