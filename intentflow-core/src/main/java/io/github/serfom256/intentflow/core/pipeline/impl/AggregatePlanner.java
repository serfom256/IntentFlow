package io.github.serfom256.intentflow.core.pipeline.impl;

import io.github.serfom256.intentflow.core.ast.Aggregate;
import io.github.serfom256.intentflow.core.ast.AggregateFunction;
import io.github.serfom256.intentflow.core.ast.ValueExpr;
import io.github.serfom256.intentflow.core.sql.plan.Select;
import io.github.serfom256.intentflow.core.sql.plan.SqlExpr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class AggregatePlanner {

    private static final String ARGUMENT = "intentflow_a";
    private static final String DERIVED = "intentflow_q";

    private AggregatePlanner() {
    }

    static List<Select.Item> selectItems(Aggregate aggregate, SqlScope scope) {
        List<Select.Item> items = new ArrayList<>();
        for (int i = 0; i < aggregate.groupBy().size(); i++) {
            SqlExpr group = ValuePlanner.plan(aggregate.groupBy().get(i), scope);
            items.add(new Select.Value(group, group instanceof SqlExpr.Column ? null : alias(i)));
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
        SqlExpr group = ValuePlanner.plan(aggregate.groupBy().get(index), scope);
        return group instanceof SqlExpr.Column ? group : new SqlExpr.Column(null, alias(index));
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

    static boolean computed(Aggregate aggregate, SqlScope scope) {
        return aggregate.groupBy().stream().anyMatch(g -> !(ValuePlanner.plan(g, scope) instanceof SqlExpr.Column));
    }

    static Select derived(Select grouped, Aggregate aggregate, List<Select.Order> order, SqlScope scope) {
        List<Select.Item> inner = new ArrayList<>();
        List<Select.Item> outer = new ArrayList<>();
        List<SqlExpr> groupBy = new ArrayList<>();
        Map<SqlExpr, SqlExpr> renamed = new HashMap<>();
        for (int i = 0; i < aggregate.groupBy().size(); i++) {
            SqlExpr column = new SqlExpr.Column(null, alias(i));
            inner.add(new Select.Value(ValuePlanner.plan(aggregate.groupBy().get(i), scope), alias(i)));
            outer.add(new Select.Value(column, null));
            groupBy.add(column);
            renamed.put(group(aggregate, i, scope), column);
        }
        SqlExpr function = new SqlExpr.CountAll();
        if (aggregate.function() != AggregateFunction.COUNT) {
            inner.add(new Select.Value(ValuePlanner.plan(Objects.requireNonNull(aggregate.argument()), scope),
                    ARGUMENT));
            function = new SqlExpr.Call(aggregate.function().name(), new SqlExpr.Column(null, ARGUMENT));
        }
        renamed.put(function(aggregate, scope), function);
        outer.add(new Select.Value(function, null));
        order.replaceAll(o -> new Select.Order(renamed.getOrDefault(o.value(), o.value()), o.direction(),
                o.nullable()));
        Select rows = Select.from(grouped.from(), inner, grouped.where());
        return Select.from(new Select.Derived(rows, DERIVED), outer, List.of()).withGroupBy(groupBy);
    }

    private static String alias(int index) {
        return "intentflow_g" + index;
    }
}
