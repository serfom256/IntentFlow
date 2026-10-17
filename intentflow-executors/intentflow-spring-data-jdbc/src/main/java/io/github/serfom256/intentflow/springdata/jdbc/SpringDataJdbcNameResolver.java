package io.github.serfom256.intentflow.springdata.jdbc;

import io.github.serfom256.intentflow.core.catalog.NameResolver;
import io.github.serfom256.intentflow.core.schema.TableFacts;
import org.springframework.data.jdbc.core.mapping.JdbcMappingContext;
import org.springframework.data.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;
import org.springframework.data.relational.core.mapping.Table;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

public final class SpringDataJdbcNameResolver implements NameResolver {

    private final RelationalMappingContext mappingContext;

    public SpringDataJdbcNameResolver() {
        this(new JdbcMappingContext());
    }

    public SpringDataJdbcNameResolver(RelationalMappingContext mappingContext) {
        this.mappingContext = mappingContext;
    }

    @Override
    public Optional<String> tableName(Class<?> type) {
        if (!type.isAnnotationPresent(Table.class)) {
            return Optional.empty();
        }
        return Optional.of(mappingContext.getRequiredPersistentEntity(type).getTableName().getReference());
    }

    @Override
    public TableFacts facts(Class<?> type) {
        if (!type.isAnnotationPresent(Table.class)) {
            return TableFacts.UNKNOWN;
        }
        RelationalPersistentProperty id = mappingContext.getRequiredPersistentEntity(type).getIdProperty();
        return id == null ? TableFacts.UNKNOWN : TableFacts.primaryKey(List.of(id.getColumnName().getReference()));
    }

    @Override
    public Optional<String> columnName(Field field) {
        Class<?> owner = field.getDeclaringClass();
        if (!owner.isAnnotationPresent(Table.class)) {
            return Optional.empty();
        }
        RelationalPersistentProperty property = mappingContext.getRequiredPersistentEntity(owner)
                .getPersistentProperty(field.getName());
        return property == null ? Optional.empty() : Optional.of(property.getColumnName().getReference());
    }
}
