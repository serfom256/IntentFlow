package io.github.serfom256.intentflow.core.pipeline.impl;

import io.github.serfom256.intentflow.core.ast.Aggregate;
import io.github.serfom256.intentflow.core.ast.AstQuery;
import io.github.serfom256.intentflow.core.ast.FilterExpr;
import io.github.serfom256.intentflow.core.ast.TopN;
import io.github.serfom256.intentflow.core.catalog.FieldType;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.pipeline.ResultShape;
import io.github.serfom256.intentflow.core.pipeline.ValidatedQuery;
import io.github.serfom256.intentflow.core.pipeline.ValidationException;
import io.github.serfom256.intentflow.core.sql.Optimization;
import io.github.serfom256.intentflow.core.sql.plan.Select;
import io.github.serfom256.intentflow.core.sql.plan.SqlExpr;
import io.github.serfom256.intentflow.core.sql.plan.SqlPredicate;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

final class QueryPlanner {

    private static final String RANK_COLUMN = "intentflow_rank";

    private QueryPlanner() {
    }

    static Select plan(ValidatedQuery query, PlanContext context) {
        AstQuery ast = query.ast();
        Aggregate aggregate = ast.aggregate();
        SqlScope scope = SqlScope.outer(context, query.service(), query.related(), false);
        if (aggregate == null && OrderPlanner.joinsForSort(query.effectiveSort(), scope)) {
            scope = SqlScope.outer(context, query.service(), query.related(), true);
        }

        FilterExpr filter = filter(ast.filter(), scope);
        List<SqlPredicate> where = scope.where(filter == null ? List.of() : List.of(FilterPlanner.plan(filter, scope)));
        List<Select.Order> order = OrderPlanner.plan(query.effectiveSort(), aggregate, scope);

        Select select;
        if (ast.top() != null) {
            select = topN(query, ast.top(), where, scope);
        } else {
            select = Select.from(scope.withJoins(scope.from()), items(aggregate, scope), where);
        }
        if (aggregate != null) {
            select = select.withGroupBy(AggregatePlanner.groupBy(aggregate, scope));
        }
        int limit = ast.take() != null ? ast.take().value() : query.service().maxTake().value();
        int offset = ast.skip() != null ? Math.max(0, ast.skip().value()) : 0;
        return select.withOrderBy(order).withPaging(new Select.Paging(limit, offset));
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
            throw new ValidationException("'top' needs window functions, which " + scope.context().dialectName()
                    + " does not support; ask for a sorted list with 'take' instead");
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
