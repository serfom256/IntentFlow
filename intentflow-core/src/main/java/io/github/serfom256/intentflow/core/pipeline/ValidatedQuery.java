package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ast.Aggregate;
import io.github.serfom256.intentflow.core.ast.AstQuery;
import io.github.serfom256.intentflow.core.ast.ExprSortKey;
import io.github.serfom256.intentflow.core.ast.FieldRef;
import io.github.serfom256.intentflow.core.ast.TopN;
import io.github.serfom256.intentflow.core.catalog.LogicalFieldName;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import io.github.serfom256.intentflow.core.catalog.factories.LogicalFieldNames;
import io.github.serfom256.intentflow.core.catalog.factories.SortKeys;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface ValidatedQuery {

    ServiceDescriptor service();

    AstQuery ast();

    /** Target services of the relations the filter uses, by relation name. */
    default Map<String, ServiceDescriptor> related() {
        return Map.of();
    }

    /**
     * The ordering to apply: the requested sort, or the default one, followed by
     * tie-breakers so pages never overlap. Entity queries append the service's
     * {@code defaultSort}, and a {@code top} query without {@code per} or a sort
     * keeps its {@code top} order; aggregate queries sort by the aggregated value
     * (descending) and then by every group field. {@code SqlBuilder.order} may
     * replace the tie-breakers of a requested sort by the table's primary key.
     */
    default List<SortKey> effectiveSort() {
        Aggregate aggregate = ast().aggregate().orElse(null);
        List<SortKey> sort = new ArrayList<>(ast().sort());
        List<SortKey> tieBreakers;
        if (aggregate == null) {
            if (sort.isEmpty()) {
                return ast().top().filter(top -> top.partition().isEmpty()).isPresent() ? topOrder()
                        : service().defaultSort();
            }
            tieBreakers = service().defaultSort();
        } else {
            if (sort.isEmpty() && !aggregate.groupBy().isEmpty()) {
                sort.add(SortKeys.of(valueField(aggregate), SortKey.Direction.DESC));
            }
            tieBreakers = aggregate.groupBy().stream()
                    .<SortKey>map(g -> g instanceof FieldRef f ? SortKeys.of(f.field(), SortKey.Direction.ASC)
                            : new ExprSortKey(g, SortKey.Direction.ASC))
                    .toList();
        }
        Set<String> used = new HashSet<>();
        sort.forEach(k -> used.add(k.field().value()));
        for (SortKey key : tieBreakers) {
            if (used.add(key.field().value())) {
                sort.add(key);
            }
        }
        return List.copyOf(sort);
    }

    /**
     * The ordering inside each {@code top} partition: the requested order followed
     * by the default sort.
     */
    default List<SortKey> topOrder() {
        TopN top = ast().top().orElse(null);
        if (top == null) {
            return List.of();
        }
        List<SortKey> order = new ArrayList<>(top.order());
        Set<String> used = new HashSet<>();
        order.forEach(k -> used.add(k.field().value()));
        for (SortKey key : service().defaultSort()) {
            if (used.add(key.field().value())) {
                order.add(key);
            }
        }
        return List.copyOf(order);
    }

    /**
     * The name sort keys use for the aggregated value, e.g. {@code count} or
     * {@code sum}.
     */
    static LogicalFieldName valueField(Aggregate aggregate) {
        return LogicalFieldNames.of(aggregate.function().keyword());
    }
}
