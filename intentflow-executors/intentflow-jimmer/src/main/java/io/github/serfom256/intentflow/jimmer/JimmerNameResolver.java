package io.github.serfom256.intentflow.jimmer;

import io.github.serfom256.intentflow.core.catalog.NameResolver;
import org.babyfish.jimmer.sql.Column;
import org.babyfish.jimmer.sql.Table;

import java.lang.reflect.Method;
import java.util.Optional;

public final class JimmerNameResolver implements NameResolver {

    @Override
    public Optional<String> tableName(Class<?> type) {
        Table table = type.getAnnotation(Table.class);
        return table == null || table.name().isEmpty() ? Optional.empty() : Optional.of(table.name());
    }

    @Override
    public Optional<String> columnName(Method method) {
        Column column = method.getAnnotation(Column.class);
        return column == null || column.name().isEmpty() ? Optional.empty() : Optional.of(column.name());
    }
}
