package io.github.serfom256.intentflow.core.catalog;

public interface SortKey {

    LogicalFieldName field();

    Direction direction();

    enum Direction {
        ASC, DESC
    }
}
