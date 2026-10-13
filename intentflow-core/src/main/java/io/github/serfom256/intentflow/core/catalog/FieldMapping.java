package io.github.serfom256.intentflow.core.catalog;

import org.jspecify.annotations.Nullable;

import java.util.List;

public interface FieldMapping {

    LogicalFieldName logical();

    ColumnName column();

    boolean queryable();

    /** Declared value type; {@link FieldType#ANY} when unknown. */
    default FieldType type() {
        return FieldType.ANY;
    }

    /** Hint for the LLM (unit, meaning, format). */
    default @Nullable String description() {
        return null;
    }

    /**
     * Java property of the result type holding this field's value (used to
     * render URI placeholders from an entity). {@code null} means the logical
     * name converted to camelCase.
     */
    default @Nullable String property() {
        return null;
    }

    /** Closed set of text values (e.g. enum constants); empty means unrestricted. */
    default List<String> allowedValues() {
        return List.of();
    }

    /**
     * {@code true} compares text with {@code ==}, {@code !=} and {@code in} exactly as written
     * instead of ignoring case, so plain column indexes apply. {@code ~} always ignores case.
     */
    default boolean caseSensitive() {
        return false;
    }
}
