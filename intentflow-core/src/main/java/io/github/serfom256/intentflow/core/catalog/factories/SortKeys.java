package io.github.serfom256.intentflow.core.catalog.factories;

import io.github.serfom256.intentflow.core.catalog.LogicalFieldName;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import io.github.serfom256.intentflow.core.catalog.impl.DefaultSortKey;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SortKeys {

    public static SortKey of(LogicalFieldName field, SortKey.Direction direction) {
        return new DefaultSortKey(field, direction);
    }
}
