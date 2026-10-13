package io.github.serfom256.intentflow.openmapper;

import io.github.serfom256.intentflow.core.catalog.NameResolver;

import java.lang.reflect.Field;
import java.util.Optional;

public final class OpenMapperNameResolver implements NameResolver {

    @Override
    public Optional<String> columnName(Field field) {
        io.github.serfom256.annotations.entity.Field mapping =
                field.getAnnotation(io.github.serfom256.annotations.entity.Field.class);
        if (mapping == null) {
            return Optional.empty();
        }
        return Optional.of(mapping.name().isEmpty() ? field.getName() : mapping.name());
    }
}
