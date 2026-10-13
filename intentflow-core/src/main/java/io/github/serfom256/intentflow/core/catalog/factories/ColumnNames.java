package io.github.serfom256.intentflow.core.catalog.factories;

import io.github.serfom256.intentflow.core.catalog.ColumnName;
import io.github.serfom256.intentflow.core.catalog.impl.DefaultColumnName;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ColumnNames {

    public static ColumnName of(String value) {
        return new DefaultColumnName(value);
    }
}
