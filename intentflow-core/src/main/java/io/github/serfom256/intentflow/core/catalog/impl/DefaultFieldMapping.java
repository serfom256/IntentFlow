package io.github.serfom256.intentflow.core.catalog.impl;

import io.github.serfom256.intentflow.core.catalog.ColumnName;
import io.github.serfom256.intentflow.core.catalog.FieldMapping;
import io.github.serfom256.intentflow.core.catalog.FieldType;
import io.github.serfom256.intentflow.core.catalog.LogicalFieldName;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public record DefaultFieldMapping(LogicalFieldName logical, ColumnName column, boolean queryable, FieldType type,
        Optional<String> description, List<String> allowedValues, Optional<String> property, boolean caseSensitive)
        implements FieldMapping {

    public DefaultFieldMapping {
        allowedValues = List.copyOf(allowedValues);
        if (!allowedValues.isEmpty() && type != FieldType.TEXT && type != FieldType.ANY) {
            throw new IllegalArgumentException(
                    "allowedValues only apply to text fields; '" + logical.value() + "' is " + type);
        }
        description = description.filter(text -> !text.isBlank());
    }

    public DefaultFieldMapping(LogicalFieldName logical, ColumnName column, boolean queryable, FieldType type,
            @Nullable String description, List<String> allowedValues, @Nullable String property,
            boolean caseSensitive) {
        this(logical, column, queryable, type, Optional.ofNullable(description), allowedValues,
                Optional.ofNullable(property), caseSensitive);
    }

    public DefaultFieldMapping(LogicalFieldName logical, ColumnName column, boolean queryable, FieldType type,
            @Nullable String description, List<String> allowedValues, @Nullable String property) {
        this(logical, column, queryable, type, description, allowedValues, property, false);
    }

    public DefaultFieldMapping(LogicalFieldName logical, ColumnName column, boolean queryable, FieldType type,
            @Nullable String description, List<String> allowedValues) {
        this(logical, column, queryable, type, description, allowedValues, null, false);
    }

    public DefaultFieldMapping(LogicalFieldName logical, ColumnName column, boolean queryable) {
        this(logical, column, queryable, FieldType.ANY, null, List.of(), null);
    }
}
