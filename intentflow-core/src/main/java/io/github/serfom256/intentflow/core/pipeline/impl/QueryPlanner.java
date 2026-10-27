package io.github.serfom256.intentflow.core.pipeline.impl;

import io.github.serfom256.intentflow.core.ErrorCode;
import io.github.serfom256.intentflow.core.ast.Aggregate;
import io.github.serfom256.intentflow.core.ast.AstQuery;
import io.github.serfom256.intentflow.core.ast.ExprSortKey;
import io.github.serfom256.intentflow.core.ast.FilterExpr;
import io.github.serfom256.intentflow.core.ast.Take;
import io.github.serfom256.intentflow.core.ast.TopN;
import io.github.serfom256.intentflow.core.catalog.FieldMapping;
import io.github.serfom256.intentflow.core.catalog.FieldType;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import io.github.serfom256.intentflow.core.catalog.factories.SortKeys;
import io.github.serfom256.intentflow.core.pipeline.ResultShape;
import io.github.serfom256.intentflow.core.pipeline.ValidatedQuery;
import io.github.serfom256.intentflow.core.pipeline.ValidationException;
import io.github.serfom256.intentflow.core.sql.Optimization;
import io.github.serfom256.intentflow.core.sql.plan.Select;
import io.github.serfom256.intentflow.core.sql.plan.SqlExpr;
import io.github.serfom256.intentflow.core.sql.plan.SqlPredicate;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class QueryPlanner {

    private static final String RANK_COLUMN = "intentflow_rank";

    private QueryPlanner() {
    }

    static Select plan(ValidatedQuery query, PlanContext context) {
        return plan(query, context, null);
    }

    static boolean keysetEligible(ValidatedQuery query, PlanContext context) {
        AstQuery ast = query.ast();
        if (!context.enabled(Optimization.KEYSET_PAGING) || ast.aggregate().isPresent() || ast.top().isPresent()) {
            return false;
        }
        SqlScope scope = SqlScope.outer(context, query.service(), query.related(), false);
        List<String> columns = new ArrayList<>();
        for (SortKey key : order(query, scope)) {
            if (key instanceof ExprSortKey || RelationPaths.prefix(key.field()) != null || !scope.notNull(key.field())
                    || !scope.ordinals(key.field()).isEmpty()) {
                return false;
            }
            columns.add(scope.field(key.field()).column().value());
        }
        return !columns.isEmpty() && scope.unique(columns);
    }

    static List<SortKey> order(ValidatedQuery query, PlanContext context) {
        return order(query, SqlScope.outer(context, query.service(), query.related(), false));
    }

    private static List<SortKey> order(ValidatedQuery query, SqlScope scope) {
        AstQuery ast = query.ast();
        List<String> key = scope.primaryKey();
        if (ast.aggregate().isPresent() || ast.sort().isEmpty() || key.isEmpty() || !scope.unique(key)) {
            return query.effectiveSort();
        }
        List<SortKey> order = new ArrayList<>(ast.sort());
        Set<String> used = new HashSet<>();
        order.forEach(k -> used.add(k.field().value()));
        for (String column : key) {
            FieldMapping field = query.service().fields().stream()
                    .filter(f -> f.column().value().equalsIgnoreCase(column)).findFirst().orElse(null);
            if (field == null) {
                return query.effectiveSort();
            }
            if (used.add(field.logical().value())) {
                SortKey.Direction direction = query.service().defaultSort().stream()
                        .filter(k -> k.field().equals(field.logical())).map(SortKey::direction).findFirst()
                        .orElse(SortKey.Direction.ASC);
                order.add(SortKeys.of(field.logical(), direction));
            }
        }
        return List.copyOf(order);
    }

    static Select plan(ValidatedQuery query, PlanContext context, @Nullable List<Object> after) {
        AstQuery ast = query.ast();
        Aggregate aggregate = ast.aggregate().orElse(null);
        SqlScope scope = SqlScope.outer(context, query.service(), query.related(), false);
        List<SortKey> sort = order(query, scope);
        if (aggregate == null && OrderPlanner.joinsForSort(sort, scope)) {
            scope = SqlScope.outer(context, query.service(), query.related(), true);
        }

        FilterExpr filter = filter(ast.filter().orElse(null), scope);
        List<SqlPredicate> where = new ArrayList<>(
                scope.where(filter == null ? List.of() : List.of(FilterPlanner.plan(filter, scope))));
        if (after != null) {
            where.add(after(sort, after, scope));
            context.applied(Optimization.KEYSET_PAGING);
        }
        List<Select.Order> order = new ArrayList<>(OrderPlanner.plan(sort, aggregate, scope));

        Select select;
        if (ast.top().isPresent()) {
            select = topN(query, ast.top().get(), where, scope);
        } else {
            select = Select.from(scope.withJoins(scope.from()), items(aggregate, scope), where);
        }
        if (aggregate != null) {
            select = select.withGroupBy(AggregatePlanner.groupBy(aggregate, scope));
            if (!context.capabilities().groupByAlias() && AggregatePlanner.computed(aggregate, scope)) {
                select = AggregatePlanner.derived(select, aggregate, order, scope);
            }
        }
        int limit = ast.take().map(Take::value).orElse(query.service().maxTake().value());
        int offset = after != null ? 0 : ast.skip().map(skip -> Math.max(0, skip.value())).orElse(0);
        return select.withOrderBy(order).withPaging(new Select.Paging(limit, offset));
    }

    private static SqlPredicate after(List<SortKey> keys, List<Object> values, SqlScope scope) {
        if (keys.size() != values.size()) {
            throw new IllegalArgumentException(
                    "keyset paging needs one value per sort key, got " + values.size() + " for " + keys.size());
        }
        List<SqlPredicate> alternatives = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            List<SqlPredicate> parts = new ArrayList<>();
            for (int j = 0; j < i; j++) {
                parts.add(new SqlPredicate.Compare(scope.column(keys.get(j).field()), "=",
                        new SqlExpr.Value(values.get(j))));
            }
            parts.add(new SqlPredicate.Compare(scope.column(keys.get(i).field()),
                    keys.get(i).direction() == SortKey.Direction.DESC ? "<" : ">", new SqlExpr.Value(values.get(i))));
            alternatives.add(parts.size() == 1 ? parts.getFirst() : new SqlPredicate.And(parts));
        }
        return alternatives.size() == 1 ? alternatives.getFirst() : new SqlPredicate.Or(alternatives);
    }

    private static @Nullable FilterExpr filter(@Nullable FilterExpr filter, SqlScope scope) {
        if (filter == null || !scope.context().enabled(Optimization.DATE_RANGES)) {
            return filter;
        }
        FilterExpr rewritten = DateRanges.rewrite(filter, f -> scope.field(f).type() == FieldType.TIMESTAMP,
                f -> RelationPaths.prefix(f) != null || !scope.notNull(f));
        if (rewritten != filter) {
            scope.context().applied(Optimization.DATE_RANGES);
        }
        return rewritten;
    }

    static Select related(ServiceDescriptor target, String column, List<?> keys, PlanContext context) {
        SqlScope scope = SqlScope.outer(context, target, Map.of(), false);
        SqlPredicate in = new SqlPredicate.In(scope.column(column),
                keys.stream().<SqlExpr>map(SqlExpr.Value::new).toList());
        return Select.from(scope.from(), List.of(scope.allColumns()), scope.where(List.of(in)));
    }

    private static List<Select.Item> items(@Nullable Aggregate aggregate, SqlScope scope) {
        if (aggregate != null) {
            return AggregatePlanner.selectItems(aggregate, scope);
        }
        PlanContext context = scope.context();
        if (context.shape() == ResultShape.KEYS && context.enabled(Optimization.KEY_PROJECTION)
                && !scope.primaryKey().isEmpty()) {
            context.applied(Optimization.KEY_PROJECTION);
            return scope.primaryKey().stream().<Select.Item>map(c -> new Select.Value(scope.column(c), null)).toList();
        }
        return List.of(scope.allColumns());
    }

    private static Select topN(ValidatedQuery query, TopN top, List<SqlPredicate> where, SqlScope scope) {
        if (!scope.context().capabilities().windowFunctions()) {
            throw new ValidationException(ErrorCode.UNSUPPORTED, "'top' needs window functions, which "
                    + scope.context().dialectName() + " does not support; ask for a sorted list with 'take' instead",
                    Map.of());
        }
        SqlExpr rank = new SqlExpr.RowNumber(top.partition().stream().map(p -> ValuePlanner.plan(p, scope)).toList(),
                OrderPlanner.plan(query.topOrder(), null, scope));
        Select ranked = Select.from(scope.from(),
                List.of(new Select.AllColumns(scope.reference()), new Select.Value(rank, RANK_COLUMN)), where);
        return Select.from(scope.withJoins(new Select.Derived(ranked, scope.reference())), items(null, scope),
                List.of(new SqlPredicate.Compare(new SqlExpr.Column(null, RANK_COLUMN), "<=",
                        new SqlExpr.Inline(top.count()))));
    }
}
