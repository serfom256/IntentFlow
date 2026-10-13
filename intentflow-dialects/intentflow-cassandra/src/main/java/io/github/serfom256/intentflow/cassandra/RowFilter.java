package io.github.serfom256.intentflow.cassandra;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.cql.Row;
import io.github.serfom256.intentflow.core.ast.And;
import io.github.serfom256.intentflow.core.ast.Compare;
import io.github.serfom256.intentflow.core.ast.ComparisonOp;
import io.github.serfom256.intentflow.core.ast.FilterExpr;
import io.github.serfom256.intentflow.core.ast.In;
import io.github.serfom256.intentflow.core.ast.IsNotNull;
import io.github.serfom256.intentflow.core.ast.IsNull;
import io.github.serfom256.intentflow.core.ast.Not;
import io.github.serfom256.intentflow.core.ast.Or;
import io.github.serfom256.intentflow.core.ast.TextLiteral;
import io.github.serfom256.intentflow.core.catalog.LogicalFieldName;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

@RequiredArgsConstructor
final class RowFilter implements Predicate<Row> {

    private final @Nullable FilterExpr filter;
    private final Map<String, CqlIdentifier> columns;
    private final Set<String> caseSensitive;

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
                Boolean v = eval(n.inner(), row);
                yield v == null ? null : !v;
            }
            case IsNull n -> value(row, n.field()) == null;
            case IsNotNull n -> value(row, n.field()) != null;
            case In in -> {
                Object value = value(row, in.field());
                if (value == null) {
                    yield null;
                }
                boolean unknown = false;
                for (var literal : in.values()) {
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
        };
    }

    private @Nullable Boolean compare(Compare c, Row row) {
        Object value = value(row, c.field());
        if (value == null) {
            return null;
        }
        if (c.op() == ComparisonOp.CONTAINS) {
            return value instanceof String s && c.value() instanceof TextLiteral t
                    ? CqlValues.lower(s).contains(CqlValues.lower(t.value()))
                    : null;
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

    private @Nullable Object value(Row row, LogicalFieldName field) {
        CqlIdentifier column = columns.get(field.value());
        if (column == null) {
            throw new IllegalArgumentException("logical field '" + field.value() + "' has no column mapping");
        }
        return row.getObject(column);
    }
}
