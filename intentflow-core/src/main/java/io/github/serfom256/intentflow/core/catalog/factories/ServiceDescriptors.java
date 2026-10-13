package io.github.serfom256.intentflow.core.catalog.factories;

import io.github.serfom256.intentflow.core.catalog.FieldMapping;
import io.github.serfom256.intentflow.core.catalog.MaxTake;
import io.github.serfom256.intentflow.core.catalog.NameResolver;
import io.github.serfom256.intentflow.core.catalog.ServiceDescription;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.catalog.ServiceRelation;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import io.github.serfom256.intentflow.core.catalog.TableName;
import io.github.serfom256.intentflow.core.catalog.UriTemplate;
import io.github.serfom256.intentflow.core.catalog.impl.AnnotatedServiceReader;
import io.github.serfom256.intentflow.core.catalog.impl.DefaultServiceDescriptor;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ServiceDescriptors {

    public static ServiceDescriptor of(
            ServiceName name,
            @Nullable ServiceDescription description,
            TableName table,
            List<FieldMapping> fields,
            List<SortKey> defaultSort,
            MaxTake maxTake,
            UriTemplate uriTemplate,
            Class<?> resultType) {
        return new DefaultServiceDescriptor(
                name, description, table, fields, defaultSort, maxTake, uriTemplate, resultType);
    }

    public static ServiceDescriptor of(
            ServiceName name,
            @Nullable ServiceDescription description,
            TableName table,
            List<FieldMapping> fields,
            List<SortKey> defaultSort,
            MaxTake maxTake,
            UriTemplate uriTemplate,
            Class<?> resultType,
            List<ServiceRelation> relations) {
        return new DefaultServiceDescriptor(
                name, description, table, fields, defaultSort, maxTake, uriTemplate, resultType, relations);
    }

    public static ServiceDescriptor fromAnnotated(Class<?> type) {
        return AnnotatedServiceReader.read(type, List.of());
    }

    public static ServiceDescriptor fromAnnotated(Class<?> type, List<NameResolver> resolvers) {
        return AnnotatedServiceReader.read(type, resolvers);
    }
}
