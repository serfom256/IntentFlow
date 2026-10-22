package io.github.serfom256.intentflow.core.sql.render;

import io.github.serfom256.intentflow.core.ErrorCode;
import io.github.serfom256.intentflow.core.ast.BoolLiteral;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.DslLiteral;
import io.github.serfom256.intentflow.core.ast.NumberLiteral;
import io.github.serfom256.intentflow.core.ast.TextLiteral;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import io.github.serfom256.intentflow.core.pipeline.SqlDialect;
import io.github.serfom256.intentflow.core.pipeline.ValidationException;
import io.github.serfom256.intentflow.core.sql.plan.Select;
import io.github.serfom256.intentflow.core.sql.plan.SqlExpr;
import io.github.serfom256.intentflow.core.sql.plan.SqlPredicate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class SqlRenderer {

    private final SqlDialect dialect;

    public SqlRenderer(SqlDialect dialect) {
        this.dialect = dialect;
    }

    public BoundQuery render(Select select) {
        return render(select, List.of());
    }

    public BoundQuery render(Select select, List<String> optimizations) {
        SqlBuffer sql = new SqlBuffer();
        select(select, sql);
        BoundQuery query = sql.toBoundQuery(optimizations);
        int max = dialect.capabilities().maxParameters();
        if (query.parameters().size() > max) {
            throw new ValidationException(ErrorCode.LIMIT_EXCEEDED,
                    "the query needs " + query.parameters().size() + " parameters, more than "
                            + dialect.name() + " accepts (" + max + ")",
                    Map.of());
        }
        return query;
    }

    private void select(Select select, SqlBuffer sql) {
        sql.text("SELECT ");
        join(select.items(), ", ", item -> item(item, sql), sql);
        sql.text(" FROM ");
        from(select.from(), sql);
        if (!select.where().isEmpty()) {
            sql.text(" WHERE ");
            join(select.where(), " AND ", p -> predicate(p, sql), sql);
        }
        if (!select.groupBy().isEmpty()) {
            sql.text(" GROUP BY ");
            join(select.groupBy(), ", ", e -> expr(e, sql), sql);
        }
        if (!select.orderBy().isEmpty()) {
            sql.text(" ORDER BY ");
            orders(select.orderBy(), sql);
        }
        if (select.paging() != null) {
            dialect.limit(sql, select.paging().limit(), select.paging().offset());
        }
    }

    private void item(Select.Item item, SqlBuffer sql) {
        switch (item) {
            case Select.AllColumns all ->
                sql.text(all.qualifier() == null ? "*" : dialect.quote(all.qualifier()) + ".*");
            case Select.Value value -> {
                expr(value.expr(), sql);
                if (value.alias() != null) {
                    sql.text(" AS ").text(dialect.quote(value.alias()));
                }
            }
        }
    }

    private void from(Select.From from, SqlBuffer sql) {
        switch (from) {
            case Select.Table table -> {
                sql.text(tableName(table.name()));
                if (table.alias() != null) {
                    sql.text(" ").text(dialect.quote(table.alias()));
                }
            }
            case Select.LeftJoin join -> {
                from(join.left(), sql);
                sql.text(" LEFT JOIN ");
                from(join.right(), sql);
                sql.text(" ON ");
                join(join.on(), " AND ", p -> predicate(p, sql), sql);
            }
            case Select.Derived derived -> {
                sql.text("(");
                select(derived.select(), sql);
                sql.text(") ").text(dialect.quote(derived.alias()));
            }
        }
    }

    private void orders(List<Select.Order> orders, SqlBuffer sql) {
        join(orders, ", ", order -> {
            if (order.nullable()) {
                dialect.orderBy(sql, () -> expr(order.value(), sql), order.direction());
            } else {
                expr(order.value(), sql);
                sql.text(order.direction() == SortKey.Direction.DESC ? " DESC" : " ASC");
            }
        }, sql);
    }

    private void predicate(SqlPredicate predicate, SqlBuffer sql) {
        switch (predicate) {
            case SqlPredicate.And and -> group(and.parts(), " AND ", sql);
            case SqlPredicate.Or or -> group(or.parts(), " OR ", sql);
            case SqlPredicate.Not not -> {
                sql.text("NOT (");
                predicate(not.inner(), sql);
                sql.text(")");
            }
            case SqlPredicate.Compare compare -> {
                expr(compare.left(), sql);
                sql.text(" " + compare.operator() + " ");
                expr(compare.right(), sql);
            }
            case SqlPredicate.IsNull isNull -> {
                expr(isNull.value(), sql);
                sql.text(isNull.negated() ? " IS NOT NULL" : " IS NULL");
            }
            case SqlPredicate.In in -> {
                expr(in.value(), sql);
                sql.text(" IN (");
                join(in.values(), ", ", e -> expr(e, sql), sql);
                sql.text(")");
            }
            case SqlPredicate.Contains contains -> dialect.contains(sql, column(contains.column()), contains.text());
            case SqlPredicate.Like like -> {
                expr(like.value(), sql);
                sql.text(" LIKE ");
                expr(like.pattern(), sql);
                sql.text(" ESCAPE '" + like.escape() + "'");
            }
            case SqlPredicate.Raw raw -> sql.text("(").text(raw.sql()).text(")");
            case SqlPredicate.Bound bound -> {
                sql.text("(");
                for (int i = 0; i < bound.values().size(); i++) {
                    Object value = bound.values().get(i);
                    sql.text(bound.segments().get(i)).param(new TextLiteral(String.valueOf(value)), value);
                }
                sql.text(bound.segments().getLast()).text(")");
            }
            case SqlPredicate.Exists exists -> {
                sql.text("EXISTS (");
                select(exists.select(), sql);
                sql.text(")");
            }
        }
    }

    private void group(List<SqlPredicate> parts, String operator, SqlBuffer sql) {
        sql.text("(");
        join(parts, operator, p -> predicate(p, sql), sql);
        sql.text(")");
    }

    private void expr(SqlExpr expr, SqlBuffer sql) {
        switch (expr) {
            case SqlExpr.Column column -> sql.text(column(column));
            case SqlExpr.Param param -> dialect.literal(sql, bindable(param.value()));
            case SqlExpr.TimestampParam timestamp -> {
                if (timestamp.bound() != null) {
                    sql.param(timestamp.value(), timestamp.bound());
                } else {
                    dialect.timestamp(sql, timestamp.value());
                }
            }
            case SqlExpr.Value value -> sql.param(new TextLiteral(String.valueOf(value.value())), value.value());
            case SqlExpr.InZone inZone -> dialect.inZone(sql, inZone.zone(), () -> expr(inZone.value(), sql));
            case SqlExpr.Null n -> sql.text("NULL");
            case SqlExpr.Inline inline -> sql.text(Long.toString(inline.value()));
            case SqlExpr.Call call -> {
                sql.text(call.function()).text("(");
                join(call.args(), ", ", e -> expr(e, sql), sql);
                sql.text(")");
            }
            case SqlExpr.CountAll c -> sql.text("COUNT(*)");
            case SqlExpr.Arithmetic arithmetic -> {
                sql.text("(");
                expr(arithmetic.left(), sql);
                sql.text(" " + arithmetic.operator() + " ");
                expr(arithmetic.right(), sql);
                sql.text(")");
            }
            case SqlExpr.Cast cast -> dialect.cast(sql, cast.type(), () -> expr(cast.value(), sql));
            case SqlExpr.Extract extract -> dialect.extract(sql, extract.unit(), () -> expr(extract.value(), sql));
            case SqlExpr.Truncate truncate -> dialect.truncate(sql, truncate.unit(), () -> expr(truncate.value(), sql));
            case SqlExpr.Length length -> dialect.length(sql, () -> expr(length.value(), sql));
            case SqlExpr.ExactText exact -> dialect.exactText(sql, () -> expr(exact.value(), sql));
            case SqlExpr.EnumName name -> {
                sql.text("CASE ");
                expr(name.ordinal(), sql);
                for (int i = 0; i < name.names().size(); i++) {
                    sql.text(" WHEN " + i + " THEN '" + name.names().get(i).replace("'", "''") + "'");
                }
                sql.text(" END");
            }
            case SqlExpr.Subquery subquery -> {
                sql.text("(");
                select(subquery.select(), sql);
                sql.text(")");
            }
            case SqlExpr.RowNumber rowNumber -> {
                sql.text("ROW_NUMBER() OVER (");
                if (!rowNumber.partition().isEmpty()) {
                    sql.text("PARTITION BY ");
                    join(rowNumber.partition(), ", ", e -> expr(e, sql), sql);
                    sql.text(" ");
                }
                sql.text("ORDER BY ");
                orders(rowNumber.order(), sql);
                sql.text(")");
            }
        }
    }

    private DslLiteral bindable(DslLiteral value) {
        if (value instanceof BoolLiteral b && !dialect.capabilities().booleans()) {
            return new NumberLiteral(b.value() ? BigDecimal.ONE : BigDecimal.ZERO);
        }
        return value;
    }

    private String tableName(String name) {
        String[] parts = name.split("\\.");
        StringBuilder quoted = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            quoted.append(i > 0 ? "." : "").append(dialect.quote(parts[i]));
        }
        return quoted.toString();
    }

    private String column(SqlExpr.Column column) {
        String name = dialect.quote(column.name());
        return column.qualifier() == null ? name : dialect.quote(column.qualifier()) + "." + name;
    }

    private static <T> void join(List<T> items, String separator, Consumer<T> each, SqlBuffer sql) {
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sql.text(separator);
            }
            each.accept(items.get(i));
        }
    }
}
