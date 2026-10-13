package io.github.serfom256.intentflow.core.pipeline.factories;

import io.github.serfom256.intentflow.core.ast.AstQuery;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.pipeline.ValidatedQuery;
import io.github.serfom256.intentflow.core.pipeline.impl.DefaultValidatedQuery;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ValidatedQueries {

    public static ValidatedQuery of(ServiceDescriptor service, AstQuery ast) {
        return new DefaultValidatedQuery(service, ast);
    }

    public static ValidatedQuery of(ServiceDescriptor service, AstQuery ast, Map<String, ServiceDescriptor> related) {
        return new DefaultValidatedQuery(service, ast, related);
    }
}
