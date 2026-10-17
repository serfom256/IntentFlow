package io.github.serfom256.intentflow.core.catalog;

import org.jspecify.annotations.Nullable;

import java.util.List;

public interface ServiceDescriptor {

    ServiceName name();

    @Nullable
    ServiceDescription description();

    TableName table();

    List<FieldMapping> fields();

    List<SortKey> defaultSort();

    MaxTake maxTake();

    UriTemplate uriTemplate();

    /**
     * Entity class the {@code QueryExecutor} maps rows of {@link #table()} into.
     */
    Class<?> resultType();

    /**
     * Links to other services whose fields can be filtered on as
     * {@code relation.field}.
     */
    default List<ServiceRelation> relations() {
        return List.of();
    }
}
