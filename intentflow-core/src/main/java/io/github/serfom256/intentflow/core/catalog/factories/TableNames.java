package io.github.serfom256.intentflow.core.catalog.factories;

import io.github.serfom256.intentflow.core.catalog.TableName;
import io.github.serfom256.intentflow.core.catalog.impl.DefaultTableName;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TableNames {

    public static TableName of(String value) {
        return new DefaultTableName(value);
    }
}
