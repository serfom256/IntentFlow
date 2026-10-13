package io.github.serfom256.intentflow.jpa;

import io.github.serfom256.intentflow.core.catalog.NameResolver;
import jakarta.persistence.Column;
import jakarta.persistence.Table;

import java.lang.reflect.Field;
import java.util.Optional;

public final class JpaNameResolver implements NameResolver {

    @Override
    public Optional<String> tableName(Class<?> type) {
        Table table = type.getAnnotation(Table.class);
        return table == null || table.name().isEmpty() ? Optional.empty() : Optional.of(table.name());
    }

    @Override
    public Optional<String> columnName(Field field) {
        Column column = field.getAnnotation(Column.class);
        return column == null || column.name().isEmpty() ? Optional.empty() : Optional.of(column.name());
    }
}
