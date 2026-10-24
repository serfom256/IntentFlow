package io.github.serfom256.intentflow.core.pipeline.impl;

import io.github.serfom256.intentflow.core.ErrorCode;
import io.github.serfom256.intentflow.core.ast.Aggregate;
import io.github.serfom256.intentflow.core.ast.ExprSortKey;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import io.github.serfom256.intentflow.core.pipeline.ValidatedQuery;
import io.github.serfom256.intentflow.core.pipeline.ValidationException;
import io.github.serfom256.intentflow.core.sql.Optimization;
import io.github.serfom256.intentflow.core.sql.plan.Select;
import io.github.serfom256.intentflow.core.sql.plan.SqlExpr;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

final class OrderPlanner {

    private OrderPlanner() {
    }

    static List<Select.Order> plan(List<SortKey> keys, @Nullable Aggregate aggregate, SqlScope scope) {
        return keys.stream().map(key -> order(key, aggregate, scope)).toList();
    }

    static boolean joinsForSort(List<SortKey> keys, SqlScope scope) {
        return scope.context().enabled(Optimization.TO_ONE_JOIN) && keys.stream()
                .filter(k -> !(k instanceof ExprSortKey))
                .map(k -> RelationPaths.prefix(k.field()))
                .anyMatch(relation -> relation != null && scope.canJoin(relation));
    }

    private static Select.Order order(SortKey key, @Nullable Aggregate aggregate, SqlScope scope) {
        if (aggregate != null) {
            int group = aggregate.groupNames().indexOf(key.field().value());
            if (group >= 0) {
                return new Select.Order(AggregatePlanner.group(aggregate, group, scope), key.direction());
            }
            if (key.field().value().equals(ValidatedQuery.valueField(aggregate).value())) {
                return new Select.Order(AggregatePlanner.function(aggregate, scope), key.direction());
            }
        }
        if (key instanceof ExprSortKey e) {
            return new Select.Order(ValuePlanner.plan(e.expression(), scope), key.direction());
        }
        String relation = RelationPaths.prefix(key.field());
        if (relation != null) {
            return new Select.Order(relatedValue(relation, key, scope), key.direction());
        }
        boolean nullable = true;
        if (scope.context().enabled(Optimization.NOT_NULL_ORDERING) && scope.notNull(key.field())) {
            scope.context().applied(Optimization.NOT_NULL_ORDERING);
            nullable = false;
        }
        return new Select.Order(scope.value(key.field()), key.direction(), nullable);
    }

    private static SqlExpr relatedValue(String relation, SortKey key, SqlScope outer) {
        if (outer.context().enabled(Optimization.TO_ONE_JOIN) && outer.canJoin(relation)) {
            outer.context().applied(Optimization.TO_ONE_JOIN);
            return outer.join(relation).value(RelationPaths.strip(key.field(), relation));
        }
        if (!outer.context().capabilities().sortBySubquery()) {
            throw new ValidationException(ErrorCode.UNSUPPORTED, "sorting by '" + key.field().value()
                    + "' needs a correlated subquery in ORDER BY, which " + outer.context().dialectName()
                    + " does not support; sort by a field of the service itself", Map.of());
        }
        SqlScope inner = outer.inner(relation);
        SqlExpr value = new SqlExpr.Call(key.direction() == SortKey.Direction.DESC ? "MAX" : "MIN",
                inner.value(RelationPaths.strip(key.field(), relation)));
        return new SqlExpr.Subquery(Select.from(inner.from(), List.of(new Select.Value(value, null)),
                inner.where(List.of(outer.correlation(relation)))));
    }
}
