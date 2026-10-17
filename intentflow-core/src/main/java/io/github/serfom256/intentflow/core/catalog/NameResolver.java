package io.github.serfom256.intentflow.core.catalog;

import io.github.serfom256.intentflow.core.schema.TableFacts;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
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

    /**
     * Column of a property declared as a method, as on interface-based entities.
     */
    default Optional<String> columnName(Method method) {
        return Optional.empty();
    }

    /**
     * Associations the mapper declares on {@code type}, such as JPA
     * {@code @ManyToOne} or OpenMapper
     * {@code @Joined}. They become relations unless an {@code @IntentRelation} with
     * the same name exists.
     */
    default List<DiscoveredRelation> relations(Class<?> type) {
        return List.of();
    }

    /**
     * Physical facts the ORM knows about the entity's table, such as its primary
     * key and NOT NULL columns. They
     * complement facts read from the database.
     */
    default TableFacts facts(Class<?> type) {
        return TableFacts.UNKNOWN;
    }

    /**
     * SQL condition the ORM applies to every query of the entity, such as a
     * soft-delete condition; IntentFlow ANDs
     * it into the SQL it generates.
     */
    default Optional<String> restriction(Class<?> type) {
        return Optional.empty();
    }
}
