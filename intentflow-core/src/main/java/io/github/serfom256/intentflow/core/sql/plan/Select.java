package io.github.serfom256.intentflow.core.sql.plan;

import io.github.serfom256.intentflow.core.catalog.SortKey;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record Select(List<Item> items, From from, List<SqlPredicate> where, List<SqlExpr> groupBy,
        List<Order> orderBy, @Nullable Paging paging) {

    public Select {
        items = List.copyOf(items);
        where = List.copyOf(where);
        groupBy = List.copyOf(groupBy);
        orderBy = List.copyOf(orderBy);
    }

    public static Select from(From from, List<Item> items, List<SqlPredicate> where) {
        return new Select(items, from, where, List.of(), List.of(), null);
    }

    public Select withGroupBy(List<SqlExpr> groupBy) {
        return new Select(items, from, where, groupBy, orderBy, paging);
    }

    public Select withOrderBy(List<Order> orderBy) {
        return new Select(items, from, where, groupBy, orderBy, paging);
    }

    public Select withPaging(Paging paging) {
        return new Select(items, from, where, groupBy, orderBy, paging);
    }

    public Select withFrom(From from) {
        return new Select(items, from, where, groupBy, orderBy, paging);
    }

    public Select withItems(List<Item> items) {
        return new Select(items, from, where, groupBy, orderBy, paging);
    }

    public sealed interface Item {
    }

    public record AllColumns(@Nullable String qualifier) implements Item {
    }

    public record Value(SqlExpr expr, @Nullable String alias) implements Item {
    }

    public sealed interface From {
    }

    public record Table(String name, @Nullable String alias) implements From {
    }

    public record Derived(Select select, String alias) implements From {
    }

    public record LeftJoin(From left, Table right, List<SqlPredicate> on) implements From {

        public LeftJoin {
            on = List.copyOf(on);
        }
    }

    public record Order(SqlExpr value, SortKey.Direction direction, boolean nullable) {

        public Order(SqlExpr value, SortKey.Direction direction) {
            this(value, direction, true);
        }
    }

    public record Paging(int limit, int offset) {
    }
}
