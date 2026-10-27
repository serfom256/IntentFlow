package io.github.serfom256.intentflow.core.catalog.impl;

import io.github.serfom256.intentflow.core.catalog.FieldMapping;
import io.github.serfom256.intentflow.core.catalog.MaxTake;
import io.github.serfom256.intentflow.core.catalog.ServiceDescription;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.catalog.ServiceRelation;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import io.github.serfom256.intentflow.core.catalog.TableName;
import io.github.serfom256.intentflow.core.catalog.UriTemplate;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public record DefaultServiceDescriptor(ServiceName name, Optional<ServiceDescription> description, TableName table,
        List<FieldMapping> fields, List<SortKey> defaultSort, MaxTake maxTake, UriTemplate uriTemplate,
        Class<?> resultType, List<ServiceRelation> relations, Optional<String> restriction)
        implements ServiceDescriptor {

    public DefaultServiceDescriptor {
        fields = List.copyOf(fields);
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("fields must be non-empty");
        }
        Set<String> logical = new HashSet<>();
        for (FieldMapping fm : fields) {
            if (!logical.add(fm.logical().value())) {
                throw new IllegalArgumentException("duplicate logical field: " + fm.logical().value());
            }
        }
        defaultSort = List.copyOf(defaultSort);
        for (SortKey key : defaultSort) {
            if (!logical.contains(key.field().value())) {
                throw new IllegalArgumentException("defaultSort field '" + key.field().value() + "' is not declared");
            }
        }
        restriction = restriction.map(String::strip).filter(sql -> !sql.isEmpty());
        relations = List.copyOf(relations);
        Set<String> names = new HashSet<>();
        for (ServiceRelation r : relations) {
            if (!names.add(r.name()) || logical.contains(r.name())) {
                throw new IllegalArgumentException("relation '" + r.name() + "' clashes with a field or relation");
            }
            if (!logical.contains(r.from().value())) {
                throw new IllegalArgumentException(
                        "relation '" + r.name() + "' links from undeclared field '" + r.from().value() + "'");
            }
        }
    }

    public DefaultServiceDescriptor(ServiceName name, @Nullable ServiceDescription description, TableName table,
            List<FieldMapping> fields, List<SortKey> defaultSort, MaxTake maxTake, UriTemplate uriTemplate,
            Class<?> resultType) {
        this(name, description, table, fields, defaultSort, maxTake, uriTemplate, resultType, List.of());
    }

    public DefaultServiceDescriptor(ServiceName name, @Nullable ServiceDescription description, TableName table,
            List<FieldMapping> fields, List<SortKey> defaultSort, MaxTake maxTake, UriTemplate uriTemplate,
            Class<?> resultType, List<ServiceRelation> relations) {
        this(name, description, table, fields, defaultSort, maxTake, uriTemplate, resultType, relations, null);
    }

    public DefaultServiceDescriptor(ServiceName name, @Nullable ServiceDescription description, TableName table,
            List<FieldMapping> fields, List<SortKey> defaultSort, MaxTake maxTake, UriTemplate uriTemplate,
            Class<?> resultType, List<ServiceRelation> relations, @Nullable String restriction) {
        this(name, Optional.ofNullable(description), table, fields, defaultSort, maxTake, uriTemplate, resultType,
                relations, Optional.ofNullable(restriction));
    }
}
