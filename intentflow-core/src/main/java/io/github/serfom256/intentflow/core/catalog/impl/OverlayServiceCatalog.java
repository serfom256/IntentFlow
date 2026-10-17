package io.github.serfom256.intentflow.core.catalog.impl;

import io.github.serfom256.intentflow.core.catalog.CatalogOverrides;
import io.github.serfom256.intentflow.core.catalog.ColumnName;
import io.github.serfom256.intentflow.core.catalog.FieldMapping;
import io.github.serfom256.intentflow.core.catalog.FieldType;
import io.github.serfom256.intentflow.core.catalog.LogicalFieldName;
import io.github.serfom256.intentflow.core.catalog.MaxTake;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.ServiceDescription;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.catalog.ServiceOverride;
import io.github.serfom256.intentflow.core.catalog.ServiceRelation;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import io.github.serfom256.intentflow.core.catalog.TableName;
import io.github.serfom256.intentflow.core.catalog.UriTemplate;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class OverlayServiceCatalog implements ServiceCatalog {

    private final ServiceCatalog declared;
    private final CatalogOverrides overrides;

    public OverlayServiceCatalog(ServiceCatalog declared, CatalogOverrides overrides) {
        this.declared = declared;
        this.overrides = overrides;
    }

    public ServiceCatalog declared() {
        return declared;
    }

    public CatalogOverrides overrides() {
        return overrides;
    }

    @Override
    public Optional<ServiceDescriptor> find(ServiceName name) {
        return declared.find(name).map(service -> overrides.find(name)
                .<ServiceDescriptor>map(o -> new Overridden(service, o))
                .orElse(service));
    }

    @Override
    public Set<ServiceName> names() {
        return declared.names();
    }

    @Override
    public int version() {
        return 31 * declared.version() + Long.hashCode(overrides.version());
    }

    private record Overridden(ServiceDescriptor service, ServiceOverride override) implements ServiceDescriptor {

        @Override
        public ServiceName name() {
            return service.name();
        }

        @Override
        public @Nullable ServiceDescription description() {
            return override.description() == null ? service.description()
                    : new DefaultServiceDescription(override.description());
        }

        @Override
        public TableName table() {
            return service.table();
        }

        @Override
        public List<FieldMapping> fields() {
            return service.fields().stream().map(f -> {
                String text = override.fieldDescriptions().get(f.logical().value());
                return text == null ? f : (FieldMapping) new DescribedField(f, text);
            }).toList();
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
            return override.uri() == null ? service.uriTemplate() : new DefaultUriTemplate(override.uri());
        }

        @Override
        public Class<?> resultType() {
            return service.resultType();
        }

        @Override
        public List<ServiceRelation> relations() {
            return service.relations();
        }
    }

    private record DescribedField(FieldMapping field, String description) implements FieldMapping {

        @Override
        public LogicalFieldName logical() {
            return field.logical();
        }

        @Override
        public ColumnName column() {
            return field.column();
        }

        @Override
        public boolean queryable() {
            return field.queryable();
        }

        @Override
        public FieldType type() {
            return field.type();
        }

        @Override
        public @Nullable String property() {
            return field.property();
        }

        @Override
        public List<String> allowedValues() {
            return field.allowedValues();
        }

        @Override
        public boolean caseSensitive() {
            return field.caseSensitive();
        }
    }
}
