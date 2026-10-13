package io.github.serfom256.intentflow.core.catalog;

import java.lang.reflect.Field;
import java.util.Optional;

/**
 * Supplies table and column names from a mapping library's own annotations
 * (JPA {@code @Table}/{@code @Column}, OpenMapper {@code @Field}, ...) so they
 * are not repeated on {@code @IntentService}/{@code @IntentField}. Explicit
 * IntentFlow values win; when every resolver returns empty, snake_case of the
 * Java name is used.
 */
public interface NameResolver {

    default Optional<String> tableName(Class<?> type) {
        return Optional.empty();
    }

    default Optional<String> columnName(Field field) {
        return Optional.empty();
    }
}
