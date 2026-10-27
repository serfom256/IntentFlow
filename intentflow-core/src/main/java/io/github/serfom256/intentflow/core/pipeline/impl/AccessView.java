package io.github.serfom256.intentflow.core.pipeline.impl;

import io.github.serfom256.intentflow.core.api.IntentContext;
import io.github.serfom256.intentflow.core.catalog.FieldMapping;
import io.github.serfom256.intentflow.core.catalog.MaxTake;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.ServiceDescription;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.catalog.ServiceRelation;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import io.github.serfom256.intentflow.core.catalog.TableName;
import io.github.serfom256.intentflow.core.catalog.UriTemplate;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceDescriptions;
import io.github.serfom256.intentflow.core.pipeline.AccessPolicy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class AccessView implements ServiceCatalog {

    private final Map<ServiceName, ServiceDescriptor> services;
    private final int version;

    private AccessView(Map<ServiceName, ServiceDescriptor> services, int version) {
        this.services = services;
        this.version = version;
    }

    static ServiceCatalog of(ServiceCatalog catalog, AccessPolicy policy, IntentContext caller) {
        if (policy == AccessPolicy.ALLOW_ALL) {
            return catalog;
        }
        Map<ServiceName, ServiceDescriptor> queryable = new LinkedHashMap<>();
        for (ServiceName name : catalog.names()) {
            catalog.find(name).filter(service -> policy.canQuery(service, caller))
                    .ifPresent(service -> queryable.put(name, service));
        }
        Map<ServiceName, ServiceDescriptor> visible = new LinkedHashMap<>();
        queryable.forEach((name, service) -> visible.put(name, visible(service, queryable, policy, caller)));
        return new AccessView(Collections.unmodifiableMap(visible), 31 * catalog.version() + fingerprint(visible));
    }

    private static ServiceDescriptor visible(ServiceDescriptor service, Map<ServiceName, ServiceDescriptor> queryable,
            AccessPolicy policy, IntentContext caller) {
        List<FieldMapping> fields = service.fields().stream().filter(f -> policy.canSee(service, f, caller)).toList();
        List<ServiceRelation> relations = service.relations().stream().filter(r -> queryable.containsKey(r.target()))
                .toList();
        String scope = policy.scope(service, caller).filter(text -> !text.isBlank()).orElse(null);
        ServiceDescription description = service.description().orElse(null);
        if (scope != null) {
            String visibleRows = "The caller sees " + scope.strip() + ", so \"my\" or \"mine\" needs no filter.";
            description = ServiceDescriptions
                    .of(description == null ? visibleRows : description.value().strip() + " " + visibleRows);
        }
        return new Visible(service, Optional.ofNullable(description), fields, relations);
    }

    private static int fingerprint(Map<ServiceName, ServiceDescriptor> visible) {
        Set<String> parts = new LinkedHashSet<>();
        visible.values().forEach(service -> {
            parts.add(service.name().value() + ":" + service.description().map(ServiceDescription::value).orElse(""));
            service.fields().forEach(f -> parts.add(service.name().value() + "." + f.logical().value()));
            service.relations().forEach(r -> parts.add(service.name().value() + ">" + r.name()));
        });
        return parts.hashCode();
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

    private record Visible(ServiceDescriptor service, Optional<ServiceDescription> description,
            List<FieldMapping> fields, List<ServiceRelation> relations) implements ServiceDescriptor {

        @Override
        public ServiceName name() {
            return service.name();
        }

        @Override
        public TableName table() {
            return service.table();
        }

        @Override
        public List<SortKey> defaultSort() {
            return service.defaultSort();
        }

        @Override
        public MaxTake maxTake() {
            return service.maxTake();
        }

        @Override
        public UriTemplate uriTemplate() {
            return service.uriTemplate();
        }

        @Override
        public Class<?> resultType() {
            return service.resultType();
        }

        @Override
        public Optional<String> restriction() {
            return service.restriction();
        }
    }
}
