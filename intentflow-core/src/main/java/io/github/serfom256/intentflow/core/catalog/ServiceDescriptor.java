package io.github.serfom256.intentflow.core.catalog;

import java.util.List;
import java.util.Optional;

public interface ServiceDescriptor {

    ServiceName name();

    Optional<ServiceDescription> description();

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

    /**
     * SQL condition every row of this service satisfies, such as
     * {@code @IntentService(restriction)} or an ORM soft-delete condition, if any.
     * It is added to every query on the service and on its relations, whichever way
     * the pipeline is built.
     */
    default Optional<String> restriction() {
        return Optional.empty();
    }
}
