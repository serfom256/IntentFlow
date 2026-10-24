package io.github.serfom256.intentflow.core.pipeline.impl;

import io.github.serfom256.intentflow.core.ast.Arith;
import io.github.serfom256.intentflow.core.ast.ArithOp;
import io.github.serfom256.intentflow.core.ast.Call;
import io.github.serfom256.intentflow.core.ast.CastExpr;
import io.github.serfom256.intentflow.core.ast.CastType;
import io.github.serfom256.intentflow.core.ast.FieldRef;
import io.github.serfom256.intentflow.core.ast.LiteralExpr;
import io.github.serfom256.intentflow.core.ast.NullLiteral;
import io.github.serfom256.intentflow.core.ast.NumberLiteral;
import io.github.serfom256.intentflow.core.ast.TextLiteral;
import io.github.serfom256.intentflow.core.ast.ValueExpr;
import io.github.serfom256.intentflow.core.sql.plan.SqlExpr;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class ValuePlanner {

    private ValuePlanner() {
    }

    static SqlExpr plan(ValueExpr expr, SqlScope scope) {
        return switch (expr) {
            case FieldRef f -> scope.value(f.field());
            case LiteralExpr l -> l.value() instanceof NullLiteral ? new SqlExpr.Null() : new SqlExpr.Param(l.value());
            case Call c -> call(c, scope);
            case Arith a -> a.op() == ArithOp.DIVIDE
                    ? new SqlExpr.Arithmetic(new SqlExpr.Cast(CastType.NUMBER, plan(a.left(), scope)), "/",
                            new SqlExpr.Call("NULLIF", plan(a.right(), scope), new SqlExpr.Inline(0)))
                    : new SqlExpr.Arithmetic(plan(a.left(), scope), a.op().symbol(), plan(a.right(), scope));
            case CastExpr c -> c.type() == CastType.TIMESTAMP && c.value() instanceof LiteralExpr l
                    && l.value() instanceof TextLiteral
                            ? new SqlExpr.TimestampParam(l.value())
                            : new SqlExpr.Cast(c.type(), plan(c.value(), scope));
        };
    }

    static SqlExpr typed(ValueExpr value, ExprTypes.Type type, SqlScope scope) {
        if (value instanceof LiteralExpr l && l.value() instanceof TextLiteral t) {
            if (type == ExprTypes.Type.TIMESTAMP) {
                return new SqlExpr.TimestampParam(t);
            }
            if (type == ExprTypes.Type.DATE) {
                return new SqlExpr.Cast(CastType.DATE, new SqlExpr.Param(t));
            }
        }
        return plan(value, scope);
    }

    private static SqlExpr local(ValueExpr value, SqlScope scope) {
        SqlExpr planned = plan(value, scope);
        return value instanceof FieldRef f && scope.zoned(f.field())
                ? new SqlExpr.InZone(planned, scope.context().zone())
                : planned;
    }

    private static SqlExpr call(Call c, SqlScope scope) {
        List<ValueExpr> args = c.args();
        return switch (c.function()) {
            case COALESCE -> {
                ExprTypes.Type type = scope.types().type(c);
                boolean decimal = type == ExprTypes.Type.NUMBER && args.stream()
                        .anyMatch(arg -> arg instanceof LiteralExpr l && l.value() instanceof NumberLiteral);
                List<SqlExpr> planned = new ArrayList<>();
                for (ValueExpr arg : args) {
                    SqlExpr value = typed(arg, type, scope);
                    planned.add(decimal && !(arg instanceof LiteralExpr) ? new SqlExpr.Cast(CastType.NUMBER, value)
                            : value);
                }
                yield new SqlExpr.Call("COALESCE", planned);
            }
            case YEAR, MONTH, DAY, HOUR -> new SqlExpr.Extract(c.function(), local(args.getFirst(), scope));
            case DATE_TRUNC -> new SqlExpr.Truncate(
                    ((TextLiteral) ((LiteralExpr) args.getFirst()).value()).value().toLowerCase(Locale.ROOT),
                    local(args.get(1), scope));
            case LENGTH -> new SqlExpr.Length(plan(args.getFirst(), scope));
            case LOWER, UPPER, ABS -> new SqlExpr.Call(c.function().name(), plan(args.getFirst(), scope));
            case ROUND -> {
                SqlExpr value = new SqlExpr.Cast(CastType.NUMBER, plan(args.getFirst(), scope));
                yield args.size() == 2
                        ? new SqlExpr.Call("ROUND", value,
                                new SqlExpr.Inline(
                                        ExprTypes.digits((NumberLiteral) ((LiteralExpr) args.get(1)).value())))
                        : new SqlExpr.Call("ROUND", value);
            }
        };
    }
}
