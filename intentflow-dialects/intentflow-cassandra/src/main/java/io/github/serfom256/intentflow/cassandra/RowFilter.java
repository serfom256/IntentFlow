package io.github.serfom256.intentflow.cassandra;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.cql.Row;
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
import io.github.serfom256.intentflow.core.ast.NullLiteral;
import io.github.serfom256.intentflow.core.ast.Or;
import io.github.serfom256.intentflow.core.ast.TextLiteral;
import io.github.serfom256.intentflow.core.catalog.LogicalFieldName;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

@RequiredArgsConstructor
final class RowFilter implements Predicate<Row> {

    private final @Nullable FilterExpr filter;
    private final Map<String, CqlIdentifier> columns;
    private final Set<String> caseSensitive;
    private final CqlExpressions expressions;

    @Override
    public boolean test(Row row) {
        return filter == null || Boolean.TRUE.equals(eval(filter, row));
    }

    private @Nullable Boolean eval(FilterExpr expr, Row row) {
        return switch (expr) {
        case And a -> {
            Boolean l = eval(a.left(), row);
            if (Boolean.FALSE.equals(l)) {
                yield false;
            }
            Boolean r = eval(a.right(), row);
            yield Boolean.FALSE.equals(r) ? Boolean.FALSE : l == null || r == null ? null : Boolean.TRUE;
        }
        case Or o -> {
            Boolean l = eval(o.left(), row);
            if (Boolean.TRUE.equals(l)) {
                yield true;
            }
            Boolean r = eval(o.right(), row);
            yield Boolean.TRUE.equals(r) ? Boolean.TRUE : l == null || r == null ? null : Boolean.FALSE;
        }
        case Not n -> {
            if (nullOperand(n.inner(), row)) {
                yield true;
            }
            Boolean v = eval(n.inner(), row);
            yield v == null ? null : !v;
        }
        case IsNull n -> value(row, n.field()) == null;
        case IsNotNull n -> value(row, n.field()) != null;
        case In in -> {
            Comparable<?> value = value(row, in.field());
            if (value == null) {
                yield null;
            }
            boolean unknown = false;
            for (DslLiteral literal : in.values()) {
                if (value instanceof LocalDateTime && literal instanceof TextLiteral t
                        && CqlValues.dateOnly(t.value()).isPresent()) {
                    Boolean sameDay = compare(new Compare(in.field(), ComparisonOp.EQ, literal), row);
                    if (Boolean.TRUE.equals(sameDay)) {
                        yield true;
                    }
                    unknown |= sameDay == null;
                    continue;
                }
                Integer c = CqlValues.compare(value, literal, true, caseSensitive.contains(in.field().value()));
                if (c == null) {
                    unknown = true;
                } else if (c == 0) {
                    yield true;
                }
            }
            yield unknown ? null : Boolean.FALSE;
        }
        case Compare c -> compare(c, row);
        case ExprCompare c -> expressions.compare(c, row);
        case ExprIn i -> expressions.in(i, row);
        };
    }

    private boolean nullOperand(FilterExpr inner, Row row) {
        return switch (inner) {
        case Compare c when c.op() != ComparisonOp.NE -> value(row, c.field()) == null;
        case In i -> value(row, i.field()) == null;
        case ExprCompare c when c.op() != ComparisonOp.NE
                && !(c.right() instanceof LiteralExpr l && l.value() instanceof NullLiteral) ->
            expressions.eval(c.left(), row) == null;
        case ExprIn i -> expressions.eval(i.value(), row) == null;
        default -> false;
        };
    }

    private @Nullable Boolean compare(Compare c, Row row) {
        Comparable<?> value = value(row, c.field());
        if (value == null) {
            return c.op() == ComparisonOp.NE ? Boolean.TRUE : null;
        }
        if (c.op() == ComparisonOp.CONTAINS) {
            return value instanceof String s && c.value() instanceof TextLiteral t
                    ? CqlValues.lower(s).contains(CqlValues.lower(t.value()))
                    : null;
        }
        if (value instanceof LocalDateTime at && c.value() instanceof TextLiteral t) {
            Optional<LocalDate> day = CqlValues.dateOnly(t.value());
            if (day.isPresent()) {
                LocalDateTime start = day.get().atStartOfDay();
                LocalDateTime end = start.plusDays(1);
                return switch (c.op()) {
                case EQ -> !at.isBefore(start) && at.isBefore(end);
                case NE -> at.isBefore(start) || !at.isBefore(end);
                case LT -> at.isBefore(start);
                case GE -> !at.isBefore(start);
                case GT -> !at.isBefore(end);
                case LE -> at.isBefore(end);
                case CONTAINS -> null;
                };
            }
        }
        boolean equality = switch (c.op()) {
        case EQ, NE -> true;
        default -> false;
        };
        Integer cmp = CqlValues.compare(value, c.value(), equality, caseSensitive.contains(c.field().value()));
        if (cmp == null) {
            return null;
        }
        return switch (c.op()) {
        case EQ -> cmp == 0;
        case NE -> cmp != 0;
        case LT -> cmp < 0;
        case LE -> cmp <= 0;
        case GT -> cmp > 0;
        case GE -> cmp >= 0;
        case CONTAINS -> throw new IllegalStateException("handled above");
        };
    }

    private @Nullable Comparable<?> value(Row row, LogicalFieldName field) {
        CqlIdentifier column = columns.get(field.value());
        if (column == null) {
            throw new IllegalArgumentException("logical field '" + field.value() + "' has no column mapping");
        }
        return expressions.comparable(row.getObject(column));
    }
}
