package io.github.serfom256.intentflow.admin;

import io.github.serfom256.intentflow.core.api.IntentTrace;
import io.github.serfom256.intentflow.core.api.factories.IntentRequests;
import io.github.serfom256.intentflow.core.catalog.CatalogOverrides;
import io.github.serfom256.intentflow.core.catalog.FieldMapping;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.catalog.ServiceNotFoundException;
import io.github.serfom256.intentflow.core.catalog.ServiceOverride;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceNames;
import io.github.serfom256.intentflow.core.catalog.impl.OverlayServiceCatalog;
import io.github.serfom256.intentflow.core.pipeline.IntentPipeline;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

public final class CatalogAdmin {

    public record FieldView(String name, String column, String type, List<String> allowedValues,
            @Nullable String declaredDescription, @Nullable String overrideDescription) {
    }

    public record RelationView(String name, String target) {
    }

    public record ServiceView(String name, String table, String entity, @Nullable String declaredDescription,
            @Nullable String overrideDescription, String declaredUri, @Nullable String overrideUri,
            List<FieldView> fields, List<RelationView> relations) {
    }

    public record ExplainView(String dsl, boolean cached, String sql, List<Object> parameters) {
    }

    public static final class InvalidOverrideException extends RuntimeException {

        private final List<String> problems;

        InvalidOverrideException(List<String> problems) {
            super(String.join("; ", problems));
            this.problems = List.copyOf(problems);
        }

        public List<String> problems() {
            return problems;
        }
    }

    private final ServiceCatalog declared;
    private final CatalogOverrides overrides;
    private final IntentPipeline pipeline;

    public CatalogAdmin(ServiceCatalog catalog, IntentPipeline pipeline) {
        if (!(catalog instanceof OverlayServiceCatalog overlay)) {
            throw new IllegalStateException("The IntentFlow admin needs the catalog wrapped with CatalogOverrides; "
                    + "a user-defined ServiceCatalog bean must use ServiceCatalogs.withOverrides(...)");
        }
        this.declared = overlay.declared();
        this.overrides = overlay.overrides();
        this.pipeline = pipeline;
    }

    public List<ServiceView> services() {
        return declared.names().stream().map(ServiceName::value).sorted().map(this::service).toList();
    }

    public ServiceView service(String name) {
        ServiceDescriptor service = declared.get(ServiceNames.of(name));
        ServiceOverride override = overrides.find(service.name()).orElse(ServiceOverride.NONE);
        List<FieldView> fields = service.fields().stream().filter(FieldMapping::queryable)
                .map(f -> new FieldView(f.logical().value(), f.column().value(), f.type().name(), f.allowedValues(),
                        f.description(), override.fieldDescriptions().get(f.logical().value())))
                .toList();
        List<RelationView> relations = service.relations().stream()
                .map(r -> new RelationView(r.name(), r.target().value()))
                .sorted(Comparator.comparing(RelationView::name)).toList();
        return new ServiceView(service.name().value(), service.table().value(), service.resultType().getSimpleName(),
                service.description() == null ? null : service.description().value(), override.description(),
                service.uriTemplate().value(), override.uri(), fields, relations);
    }

    public ServiceView update(String name, ServiceOverride override) {
        ServiceDescriptor service = declared.get(ServiceNames.of(name));
        List<String> problems = override.problems(service);
        if (!problems.isEmpty()) {
            throw new InvalidOverrideException(problems);
        }
        overrides.put(service.name(), override);
        return service(name);
    }

    public ServiceView reset(String name) {
        ServiceDescriptor service = declared.get(ServiceNames.of(name));
        overrides.remove(service.name());
        return service(name);
    }

    public ExplainView explain(String question, @Nullable String scope) {
        IntentTrace trace;
        if (scope == null || scope.isBlank()) {
            trace = pipeline.explain(IntentRequests.of(question));
        } else {
            ServiceDescriptor service = declared.find(ServiceNames.of(scope))
                    .orElseThrow(() -> new ServiceNotFoundException(ServiceNames.of(scope)));
            trace = pipeline.explain(IntentRequests.of(question), service.resultType());
        }
        return new ExplainView(trace.dsl(), trace.cached(), trace.query(), trace.parameters());
    }
}
