package io.github.serfom256.intentflow.core.catalog;

import java.util.List;
import java.util.Optional;

public interface FieldMapping {

    LogicalFieldName logical();

    ColumnName column();

    boolean queryable();

    /** Declared value type; {@link FieldType#ANY} when unknown. */
    default FieldType type() {
        return FieldType.ANY;
    }

    /** Hint for the LLM (unit, meaning, format). */
    default Optional<String> description() {
        return Optional.empty();
    }

    /**
     * Java property of the result type holding this field's value (used to render
     * URI placeholders from an entity). Empty means the logical name converted to
     * camelCase.
     */
    default Optional<String> property() {
        return Optional.empty();
    }

    /**
     * Closed set of text values (e.g. enum constants); empty means unrestricted.
     */
    default List<String> allowedValues() {
        return List.of();
    }

    /**
     * {@code true} compares text with {@code ==}, {@code !=} and {@code in} exactly
     * as written instead of ignoring case, so plain column indexes apply. {@code ~}
     * always ignores case.
     */
    default boolean caseSensitive() {
        return false;
    }
}
