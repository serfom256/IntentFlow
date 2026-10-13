package io.github.serfom256.intentflow.core.catalog.factories;

import io.github.serfom256.intentflow.core.catalog.ColumnName;
import io.github.serfom256.intentflow.core.catalog.FieldMapping;
import io.github.serfom256.intentflow.core.catalog.FieldType;
import io.github.serfom256.intentflow.core.catalog.LogicalFieldName;
import io.github.serfom256.intentflow.core.catalog.impl.DefaultFieldMapping;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FieldMappings {

    public static FieldMapping of(LogicalFieldName logical, ColumnName column, boolean queryable) {
        return new DefaultFieldMapping(logical, column, queryable);
    }

    public static FieldMapping of(LogicalFieldName logical, ColumnName column, boolean queryable,
                                  FieldType type) {
        return new DefaultFieldMapping(logical, column, queryable, type, null, List.of());
    }

    public static FieldMapping of(LogicalFieldName logical, ColumnName column, boolean queryable,
                                  FieldType type, @Nullable String description,
                                  List<String> allowedValues) {
        return new DefaultFieldMapping(logical, column, queryable, type, description, allowedValues);
    }
}
