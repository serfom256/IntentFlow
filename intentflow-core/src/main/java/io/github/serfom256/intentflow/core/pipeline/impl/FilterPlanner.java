package io.github.serfom256.intentflow.core.pipeline.impl;

import io.github.serfom256.intentflow.core.ast.And;
import io.github.serfom256.intentflow.core.ast.Compare;
import io.github.serfom256.intentflow.core.ast.ComparisonOp;
import io.github.serfom256.intentflow.core.ast.DslLiteral;
import io.github.serfom256.intentflow.core.ast.ExprCompare;
import io.github.serfom256.intentflow.core.ast.ExprIn;
import io.github.serfom256.intentflow.core.ast.FilterExpr;
import io.github.serfom256.intentflow.core.ast.In;
import io.github.serfom256.intentflow.core.ast.IsNotNull;
import io.github.serfom256.intentflow.core.ast.IsNull;
import io.github.serfom256.intentflow.core.ast.LiteralExpr;
import io.github.serfom256.intentflow.core.ast.Not;
import io.github.serfom256.intentflow.core.ast.Or;
import io.github.serfom256.intentflow.core.ast.TextLiteral;
import io.github.serfom256.intentflow.core.catalog.FieldMapping;
import io.github.serfom256.intentflow.core.catalog.FieldType;
import io.github.serfom256.intentflow.core.catalog.LogicalFieldName;
import io.github.serfom256.intentflow.core.sql.Optimization;
import io.github.serfom256.intentflow.core.sql.plan.Select;
import io.github.serfom256.intentflow.core.sql.plan.SqlExpr;
import io.github.serfom256.intentflow.core.sql.plan.SqlPredicate;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

final class FilterPlanner {

    private FilterPlanner() {
    }

    static SqlPredicate plan(FilterExpr expr, SqlScope scope) {
        if (scope.isOuter() && expr instanceof And) {
            return conjunction(RelationPaths.conjuncts(expr), scope);
        }
        String relation = scope.isOuter() ? RelationPaths.relationOf(expr) : null;
        if (relation != null) {
            return exists(relation, List.of(expr), scope);
        }
        return switch (expr) {
            case And a -> new SqlPredicate.And(List.of(plan(a.left(), scope), plan(a.right(), scope)));
            case Or o -> new SqlPredicate.Or(List.of(plan(o.left(), scope), plan(o.right(), scope)));
            case Not n -> new SqlPredicate.Not(plan(n.inner(), scope));
            case Compare c -> compare(c, scope);
            case In i -> in(i, scope);
            case IsNull i -> new SqlPredicate.IsNull(scope.column(i.field()), false);
            case IsNotNull i -> new SqlPredicate.IsNull(scope.column(i.field()), true);
            case ExprCompare c -> exprCompare(c, scope);
            case ExprIn i -> exprIn(i, scope);
        };
    }

    private static SqlPredicate conjunction(List<FilterExpr> parts, SqlScope scope) {
        Map<String, List<FilterExpr>> byRelation = new LinkedHashMap<>();
        List<Object> order = new ArrayList<>();
        for (FilterExpr part : parts) {
            String relation = RelationPaths.relationOf(part);
            if (relation == null) {
                order.add(part);
            } else {
                byRelation.computeIfAbsent(relation, r -> {
                    order.add(r);
                    return new ArrayList<>();
                }).add(part);
            }
        }
        List<SqlPredicate> planned = new ArrayList<>();
        for (Object item : order) {
            planned.add(item instanceof String relation
                    ? exists(relation, byRelation.get(relation), scope)
                    : plan((FilterExpr) item, scope));
        }
        return planned.size() == 1 ? planned.getFirst() : new SqlPredicate.And(planned);
    }

    private static SqlPredicate exists(String relation, List<FilterExpr> parts, SqlScope outer) {
        SqlScope inner = outer.inner(relation);
        List<SqlPredicate> where = new ArrayList<>();
        where.add(outer.correlation(relation));
        parts.forEach(part -> where.add(plan(RelationPaths.stripRelation(part, relation), inner)));
        return new SqlPredicate.Exists(Select.from(inner.from(),
                List.of(new Select.Value(new SqlExpr.Inline(1), null)), inner.where(where)));
    }

    private static SqlPredicate compare(Compare c, SqlScope scope) {
        SqlExpr.Column column = scope.column(c.field());
        FieldMapping fm = scope.field(c.field());
        if (c.op() == ComparisonOp.CONTAINS) {
            if (!(c.value() instanceof TextLiteral t)) {
                throw new IllegalArgumentException(
                        "'~' (CONTAINS) requires a text value; got " + c.value().getClass().getSimpleName());
            }
            return new SqlPredicate.Contains(column, t.value());
        }
        String op = sqlOp(c.op());
        if (fm.type() == FieldType.TIMESTAMP && c.value() instanceof TextLiteral t) {
            return Timestamps.isDateOnly(t.value())
                    ? day(column, c.op(), t.value())
                    : new SqlPredicate.Compare(column, op, new SqlExpr.TimestampParam(t));
        }
        if (caseInsensitive(fm, c.value()) && (c.op() == ComparisonOp.EQ || c.op() == ComparisonOp.NE)) {
            String exact = exactValue(scope, c.field(), (TextLiteral) c.value());
            if (exact == null) {
                return new SqlPredicate.Compare(lower(column), op, lower(new SqlExpr.Param(c.value())));
            }
            scope.context().applied(Optimization.EXACT_VALUES);
            return new SqlPredicate.Compare(column, op, new SqlExpr.Param(new TextLiteral(exact)));
        }
        if (exactText(fm, c.value())) {
            return new SqlPredicate.Compare(column, op, new SqlExpr.ExactText(new SqlExpr.Param(c.value())));
        }
        return new SqlPredicate.Compare(column, op, new SqlExpr.Param(c.value()));
    }

    private static SqlPredicate in(In in, SqlScope scope) {
        List<DslLiteral> literals = in.values();
        FieldMapping fm = scope.field(in.field());
        if (fm.type() == FieldType.TIMESTAMP
                && literals.stream().anyMatch(v -> v instanceof TextLiteral t && Timestamps.isDateOnly(t.value()))) {
            return new SqlPredicate.Or(literals.stream()
                    .map(v -> compare(new Compare(in.field(), ComparisonOp.EQ, v), scope)).toList());
        }
        boolean lower = literals.stream().allMatch(v -> caseInsensitive(fm, v));
        if (lower) {
            List<String> exact = literals.stream().map(v -> exactValue(scope, in.field(), (TextLiteral) v)).toList();
            if (!exact.contains(null)) {
                scope.context().applied(Optimization.EXACT_VALUES);
                return new SqlPredicate.In(scope.column(in.field()),
                        exact.stream().<SqlExpr>map(v -> new SqlExpr.Param(new TextLiteral(v))).toList());
            }
        }
        Function<DslLiteral, SqlExpr> value;
        if (lower) {
            value = v -> lower(new SqlExpr.Param(v));
        } else if (fm.type() == FieldType.TIMESTAMP) {
            value = SqlExpr.TimestampParam::new;
        } else {
            value = v -> exactText(fm, v) ? new SqlExpr.ExactText(new SqlExpr.Param(v)) : new SqlExpr.Param(v);
        }
        SqlExpr column = scope.column(in.field());
        return new SqlPredicate.In(lower ? lower(column) : column, literals.stream().map(value).toList());
    }

    private static SqlPredicate exprCompare(ExprCompare c, SqlScope scope) {
        ExprTypes.Type left = scope.types().type(c.left());
        ExprTypes.Type right = scope.types().type(c.right());
        SqlExpr leftValue = ValuePlanner.plan(c.left(), scope);
        if (right == ExprTypes.Type.NULL) {
            return new SqlPredicate.IsNull(leftValue, c.op() != ComparisonOp.EQ);
        }
        if (c.op() == ComparisonOp.CONTAINS) {
            TextLiteral text = (TextLiteral) ((LiteralExpr) c.right()).value();
            return new SqlPredicate.Like(lower(leftValue),
                    lower(new SqlExpr.Param(new TextLiteral(containing(text.value())))), '!');
        }
        String op = sqlOp(c.op());
        boolean temporal = left == ExprTypes.Type.TIMESTAMP || left == ExprTypes.Type.DATE;
        if (temporal && c.right() instanceof LiteralExpr l && l.value() instanceof TextLiteral t) {
            return left == ExprTypes.Type.TIMESTAMP && Timestamps.isDateOnly(t.value())
                    ? day(leftValue, c.op(), t.value())
                    : new SqlPredicate.Compare(leftValue, op, ValuePlanner.typed(c.right(), left, scope));
        }
        boolean text = left == ExprTypes.Type.TEXT || right == ExprTypes.Type.TEXT;
        if (text && (c.op() == ComparisonOp.EQ || c.op() == ComparisonOp.NE)) {
            return new SqlPredicate.Compare(lower(leftValue), op, lower(ValuePlanner.plan(c.right(), scope)));
        }
        return new SqlPredicate.Compare(leftValue, op, ValuePlanner.plan(c.right(), scope));
    }

    private static SqlPredicate exprIn(ExprIn in, SqlScope scope) {
        ExprTypes.Type type = scope.types().type(in.value());
        if (type == ExprTypes.Type.TIMESTAMP || type == ExprTypes.Type.DATE) {
            return new SqlPredicate.Or(in.values().stream()
                    .map(v -> exprCompare(new ExprCompare(in.value(), ComparisonOp.EQ, new LiteralExpr(v)), scope))
                    .toList());
        }
        boolean lower = type == ExprTypes.Type.TEXT;
        SqlExpr value = ValuePlanner.plan(in.value(), scope);
        return new SqlPredicate.In(lower ? lower(value) : value, in.values().stream()
                .map(v -> lower ? lower(new SqlExpr.Param(v)) : (SqlExpr) new SqlExpr.Param(v)).toList());
    }

    private static SqlPredicate day(SqlExpr value, ComparisonOp op, String day) {
        SqlExpr start = new SqlExpr.TimestampParam(new TextLiteral(day.strip()));
        SqlExpr end = new SqlExpr.TimestampParam(new TextLiteral(Timestamps.nextDay(day)));
        return switch (op) {
            case EQ -> new SqlPredicate.And(List.of(new SqlPredicate.Compare(value, ">=", start),
                    new SqlPredicate.Compare(value, "<", end)));
            case NE -> new SqlPredicate.Or(List.of(new SqlPredicate.Compare(value, "<", start),
                    new SqlPredicate.Compare(value, ">=", end)));
            case LT, GE -> new SqlPredicate.Compare(value, sqlOp(op), start);
            case GT -> new SqlPredicate.Compare(value, ">=", end);
            case LE -> new SqlPredicate.Compare(value, "<", end);
            case CONTAINS -> throw new IllegalArgumentException("'~' does not apply to timestamps");
        };
    }

    private static @Nullable String exactValue(SqlScope scope, LogicalFieldName field, TextLiteral literal) {
        if (!scope.context().enabled(Optimization.EXACT_VALUES)) {
            return null;
        }
        return scope.exactValues(field).stream().filter(v -> v.equalsIgnoreCase(literal.value())).findFirst()
                .orElse(null);
    }

    private static SqlExpr lower(SqlExpr value) {
        return new SqlExpr.Call("LOWER", value);
    }

    private static String containing(String text) {
        StringBuilder sb = new StringBuilder(text.length() + 2).append('%');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '!' || c == '%' || c == '_') {
                sb.append('!');
            }
            sb.append(c);
        }
        return sb.append('%').toString();
    }

    private static boolean caseInsensitive(FieldMapping fm, DslLiteral value) {
        return value instanceof TextLiteral && !fm.caseSensitive()
                && (fm.type() == FieldType.TEXT || fm.type() == FieldType.ANY);
    }

    private static boolean exactText(FieldMapping fm, DslLiteral value) {
        return value instanceof TextLiteral && fm.caseSensitive()
                && (fm.type() == FieldType.TEXT || fm.type() == FieldType.ANY);
    }

    private static String sqlOp(ComparisonOp op) {
        return switch (op) {
            case EQ -> "=";
            case NE -> "<>";
            case LT -> "<";
            case LE -> "<=";
            case GT -> ">";
            case GE -> ">=";
            case CONTAINS -> throw new IllegalStateException("CONTAINS handled separately");
        };
    }
}
