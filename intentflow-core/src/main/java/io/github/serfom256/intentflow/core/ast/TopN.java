package io.github.serfom256.intentflow.core.ast;

import io.github.serfom256.intentflow.core.catalog.SortKey;

import java.util.List;

public record TopN(int count, List<ValueExpr> partition, List<SortKey> order) {

    public TopN {
        partition = List.copyOf(partition);
        order = List.copyOf(order);
        if (count < 1) {
            throw new IllegalArgumentException("top needs a positive count");
        }
        if (order.isEmpty()) {
            throw new IllegalArgumentException("top needs an order");
        }
    }
}
