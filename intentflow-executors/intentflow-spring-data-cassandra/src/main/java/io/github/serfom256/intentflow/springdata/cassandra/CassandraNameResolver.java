package io.github.serfom256.intentflow.springdata.cassandra;

import io.github.serfom256.intentflow.core.catalog.NameResolver;
import org.springframework.data.cassandra.core.mapping.CassandraMappingContext;
import org.springframework.data.cassandra.core.mapping.CassandraPersistentEntity;
import org.springframework.data.cassandra.core.mapping.CassandraPersistentProperty;
import org.springframework.data.cassandra.core.mapping.Table;

import java.lang.reflect.Field;
import java.util.Optional;

public final class CassandraNameResolver implements NameResolver {

    private final CassandraMappingContext mappingContext;

    public CassandraNameResolver() {
        this(new CassandraMappingContext());
    }

    public CassandraNameResolver(CassandraMappingContext mappingContext) {
        this.mappingContext = mappingContext;
    }

    @Override
    public Optional<String> tableName(Class<?> type) {
        if (!type.isAnnotationPresent(Table.class)) {
            return Optional.empty();
        }
        return Optional.of(mappingContext.getRequiredPersistentEntity(type).getTableName().asInternal());
    }

    @Override
    public Optional<String> columnName(Field field) {
        Class<?> owner = field.getDeclaringClass();
        if (!owner.isAnnotationPresent(Table.class)) {
            return Optional.empty();
        }
        CassandraPersistentEntity<?> entity = mappingContext.getRequiredPersistentEntity(owner);
        CassandraPersistentProperty property = entity.getPersistentProperty(field.getName());
        return property == null || property.getColumnName() == null ? Optional.empty()
                : Optional.of(property.getRequiredColumnName().asInternal());
    }
}
