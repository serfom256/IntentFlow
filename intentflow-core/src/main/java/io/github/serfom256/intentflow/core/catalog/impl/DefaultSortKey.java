package io.github.serfom256.intentflow.core.catalog.impl;

import io.github.serfom256.intentflow.core.catalog.LogicalFieldName;
import io.github.serfom256.intentflow.core.catalog.SortKey;

public record DefaultSortKey(LogicalFieldName field, SortKey.Direction direction)
        implements SortKey {
}
