package io.github.serfom256.intentflow.core.catalog.factories;

import io.github.serfom256.intentflow.core.catalog.LogicalFieldName;
import io.github.serfom256.intentflow.core.catalog.impl.DefaultLogicalFieldName;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LogicalFieldNames {

    public static LogicalFieldName of(String value) {
        return new DefaultLogicalFieldName(value);
    }
}
