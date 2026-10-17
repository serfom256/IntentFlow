package io.github.serfom256.intentflow.core.pipeline.impl;

import io.github.serfom256.intentflow.core.ast.Aggregate;
import io.github.serfom256.intentflow.core.ast.AggregateFunction;
import io.github.serfom256.intentflow.core.ast.FieldRef;
import io.github.serfom256.intentflow.core.ast.ValueExpr;
import io.github.serfom256.intentflow.core.sql.plan.Select;
import io.github.serfom256.intentflow.core.sql.plan.SqlExpr;

import java.util.ArrayList;
import java.util.List;

final class AggregatePlanner {

    private AggregatePlanner() {
    }

    static List<Select.Item> selectItems(Aggregate aggregate, SqlScope scope) {
        List<Select.Item> items = new ArrayList<>();
        for (int i = 0; i < aggregate.groupBy().size(); i++) {
            ValueExpr group = aggregate.groupBy().get(i);
            items.add(new Select.Value(ValuePlanner.plan(group, scope), group instanceof FieldRef ? null : alias(i)));
        }
        items.add(new Select.Value(function(aggregate, scope), null));
        return items;
    }

    static List<SqlExpr> groupBy(Aggregate aggregate, SqlScope scope) {
        List<SqlExpr> groups = new ArrayList<>();
        for (int i = 0; i < aggregate.groupBy().size(); i++) {
            groups.add(group(aggregate, i, scope));
        }
        return groups;
    }

    static SqlExpr group(Aggregate aggregate, int index, SqlScope scope) {
        ValueExpr group = aggregate.groupBy().get(index);
        return group instanceof FieldRef ? ValuePlanner.plan(group, scope) : new SqlExpr.Column(null, alias(index));
    }

    static SqlExpr function(Aggregate aggregate, SqlScope scope) {
        if (aggregate.function() == AggregateFunction.COUNT) {
            return new SqlExpr.CountAll();
        }
        ValueExpr argument = aggregate.argument();
        if (argument == null) {
            throw new IllegalStateException(aggregate.function().keyword() + " needs a field");
        }
        return new SqlExpr.Call(aggregate.function().name(), ValuePlanner.plan(argument, scope));
    }

    private static String alias(int index) {
        return "intentflow_g" + index;
    }
}
