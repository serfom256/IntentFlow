package io.github.serfom256.intentflow.core.pipeline.impl;

import io.github.serfom256.intentflow.core.ast.AstQuery;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.pipeline.ValidatedQuery;

import java.util.Map;

public record DefaultValidatedQuery(ServiceDescriptor service, AstQuery ast, Map<String, ServiceDescriptor> related)
        implements ValidatedQuery {

    public DefaultValidatedQuery {
        related = Map.copyOf(related);
    }

    public DefaultValidatedQuery(ServiceDescriptor service, AstQuery ast) {
        this(service, ast, Map.of());
    }
}
