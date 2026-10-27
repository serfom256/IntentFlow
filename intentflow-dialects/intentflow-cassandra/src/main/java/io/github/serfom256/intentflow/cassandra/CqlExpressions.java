package io.github.serfom256.intentflow.cassandra;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.cql.Row;
import io.github.serfom256.intentflow.core.ast.Arith;
import io.github.serfom256.intentflow.core.ast.BoolLiteral;
import io.github.serfom256.intentflow.core.ast.Call;
import io.github.serfom256.intentflow.core.ast.CastExpr;
import io.github.serfom256.intentflow.core.ast.CastType;
import io.github.serfom256.intentflow.core.ast.ComparisonOp;
import io.github.serfom256.intentflow.core.ast.DslLiteral;
import io.github.serfom256.intentflow.core.ast.ExprCompare;
import io.github.serfom256.intentflow.core.ast.ExprFunction;
import io.github.serfom256.intentflow.core.ast.ExprIn;
import io.github.serfom256.intentflow.core.ast.FieldRef;
import io.github.serfom256.intentflow.core.ast.LiteralExpr;
import io.github.serfom256.intentflow.core.ast.NullLiteral;
import io.github.serfom256.intentflow.core.ast.NumberLiteral;
import io.github.serfom256.intentflow.core.ast.TextLiteral;
import io.github.serfom256.intentflow.core.ast.ValueExpr;
import io.github.serfom256.intentflow.core.catalog.FieldType;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class CqlExpressions {

    private final Map<String, CqlIdentifier> columns;
    private final Map<String, FieldType> types;
    private final ZoneId zone;

    CqlExpressions(Map<String, CqlIdentifier> columns, Map<String, FieldType> types, ZoneId zone) {
        this.columns = columns;
        this.types = types;
        this.zone = zone;
    }

    @Nullable
    Object eval(ValueExpr expr, Row row) {
        return switch (expr) {
        case FieldRef f -> comparable(row.getObject(column(f)));
        case LiteralExpr l -> literal(l.value());
        case Call c -> call(c, row);
        case Arith a -> arith(a, row);
        case CastExpr c -> cast(eval(c.value(), row), c.type());
        };
    }

    @Nullable
    Comparable<?> comparable(@Nullable Object value) {
        return CqlValues.comparable(value, zone);
    }

    @Nullable
    Boolean compare(ExprCompare c, Row row) {
        Object left = eval(c.left(), row);
        if (c.right() instanceof LiteralExpr l && l.value() instanceof NullLiteral) {
            return c.op() == ComparisonOp.EQ ? left == null : left != null;
        }
        Object right = eval(c.right(), row);
        if (left == null && c.op() == ComparisonOp.NE) {
            return true;
        }
        if (left == null || right == null) {
            return null;
        }
        if (c.op() == ComparisonOp.CONTAINS) {
            return left instanceof String s && right instanceof String t
                    ? CqlValues.lower(s).contains(CqlValues.lower(t))
                    : null;
        }
        if (left instanceof LocalDateTime at && right instanceof String text) {
            LocalDate day = CqlValues.dateOnly(text).orElse(null);
            if (day != null) {
                return dayCompare(c.op(), at, day);
            }
            right = CqlValues.parseTimestamp(text).orElse(null);
        } else if (left instanceof LocalDate && right instanceof String text) {
            right = CqlValues.parseTimestamp(text).map(LocalDateTime::toLocalDate).orElse(null);
        }
        if (right == null) {
            return null;
        }
        Integer cmp = order(left, right, c.op() == ComparisonOp.EQ || c.op() == ComparisonOp.NE);
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
        case CONTAINS -> throw new IllegalStateException();
        };
    }

    @Nullable
    Boolean in(ExprIn in, Row row) {
        boolean unknown = false;
        for (DslLiteral value : in.values()) {
            Boolean match = compare(new ExprCompare(in.value(), ComparisonOp.EQ, new LiteralExpr(value)), row);
            if (Boolean.TRUE.equals(match)) {
                return true;
            }
            unknown |= match == null;
        }
        return unknown ? null : Boolean.FALSE;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    static @Nullable Integer order(Object left, Object right, boolean ignoreCase) {
        if (left instanceof String a && right instanceof String b) {
            return ignoreCase ? CqlValues.lower(a).compareTo(CqlValues.lower(b)) : a.compareTo(b);
        }
        if (left instanceof LocalDate d && right instanceof LocalDateTime) {
            left = d.atStartOfDay();
        }
        if (left instanceof LocalDateTime && right instanceof LocalDate d) {
            right = d.atStartOfDay();
        }
        if (left.getClass() != right.getClass() || !(left instanceof Comparable)) {
            return null;
        }
        return ((Comparable) left).compareTo(right);
    }

    private static Boolean dayCompare(ComparisonOp op, LocalDateTime at, LocalDate day) {
        LocalDateTime start = day.atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        return switch (op) {
        case EQ -> !at.isBefore(start) && at.isBefore(end);
        case NE -> at.isBefore(start) || !at.isBefore(end);
        case LT -> at.isBefore(start);
        case GE -> !at.isBefore(start);
        case GT -> !at.isBefore(end);
        case LE -> at.isBefore(end);
        case CONTAINS -> throw new IllegalStateException();
        };
    }

    private @Nullable Object call(Call c, Row row) {
        List<ValueExpr> args = c.args();
        if (c.function() == ExprFunction.COALESCE) {
            boolean temporal = args.stream().anyMatch(this::temporal);
            for (ValueExpr arg : args) {
                Object value = eval(arg, row);
                if (value != null) {
                    return temporal && value instanceof String s ? CqlValues.parseTimestamp(s).orElse(null) : value;
                }
            }
            return null;
        }
        if (c.function() == ExprFunction.DATE_TRUNC) {
            String unit = ((String) eval(args.getFirst(), row)).toLowerCase(Locale.ROOT);
            Object value = eval(args.get(1), row);
            if (value == null) {
                return null;
            }
            LocalDateTime at = timestamp(value);
            return switch (unit) {
            case "year" -> at.withDayOfYear(1).truncatedTo(ChronoUnit.DAYS);
            case "month" -> at.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
            case "day" -> at.truncatedTo(ChronoUnit.DAYS);
            case "hour" -> at.truncatedTo(ChronoUnit.HOURS);
            default -> throw new IllegalArgumentException("unsupported date_trunc unit: " + unit);
            };
        }
        Object value = eval(args.getFirst(), row);
        if (value == null) {
            return null;
        }
        return switch (c.function()) {
        case YEAR -> BigDecimal.valueOf(timestamp(value).getYear());
        case MONTH -> BigDecimal.valueOf(timestamp(value).getMonthValue());
        case DAY -> BigDecimal.valueOf(timestamp(value).getDayOfMonth());
        case HOUR -> BigDecimal.valueOf(timestamp(value).getHour());
        case LENGTH -> BigDecimal.valueOf(((String) value).codePointCount(0, ((String) value).length()));
        case LOWER -> CqlValues.lower((String) value);
        case UPPER -> ((String) value).toUpperCase(Locale.ROOT);
        case ABS -> ((BigDecimal) value).abs();
        case ROUND -> ((BigDecimal) value).setScale(
                args.size() == 2 ? ((NumberLiteral) ((LiteralExpr) args.get(1)).value()).value().intValueExact() : 0,
                RoundingMode.HALF_UP);
        case COALESCE, DATE_TRUNC -> throw new IllegalStateException();
        };
    }

    private @Nullable Object arith(Arith a, Row row) {
        Object left = eval(a.left(), row);
        Object right = eval(a.right(), row);
        if (!(left instanceof BigDecimal l) || !(right instanceof BigDecimal r)) {
            return null;
        }
        return switch (a.op()) {
        case ADD -> l.add(r);
        case SUBTRACT -> l.subtract(r);
        case MULTIPLY -> l.multiply(r);
        case DIVIDE -> r.signum() == 0 ? null : l.divide(r, 20, RoundingMode.HALF_UP);
        };
    }

    private static @Nullable Object cast(@Nullable Object value, CastType type) {
        if (value == null) {
            return null;
        }
        return switch (type) {
        case NUMBER -> number(value);
        case INTEGER -> number(value).setScale(0, RoundingMode.HALF_UP);
        case TEXT -> value instanceof BigDecimal n ? n.toPlainString() : value.toString();
        case TIMESTAMP -> timestamp(value instanceof String s
                ? CqlValues.parseTimestamp(s).orElseThrow(() -> new IllegalArgumentException("not a timestamp: " + s))
                : value);
        case DATE -> value instanceof String s
                ? CqlValues.parseTimestamp(s).orElseThrow(() -> new IllegalArgumentException("not a date: " + s))
                        .toLocalDate()
                : timestamp(value).toLocalDate();
        };
    }

    private static BigDecimal number(Object value) {
        if (value instanceof BigDecimal n) {
            return n;
        }
        try {
            return new BigDecimal(value.toString().strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("cannot cast '" + value + "' to a number", e);
        }
    }

    private static LocalDateTime timestamp(Object value) {
        return switch (value) {
        case LocalDateTime t -> t;
        case LocalDate d -> d.atStartOfDay();
        default -> throw new IllegalArgumentException("not a timestamp: " + value);
        };
    }

    private boolean temporal(ValueExpr expr) {
        return switch (expr) {
        case FieldRef f -> types.get(f.field().value()) == FieldType.TIMESTAMP;
        case CastExpr c -> c.type() == CastType.TIMESTAMP || c.type() == CastType.DATE;
        case Call c -> c.function() == ExprFunction.DATE_TRUNC
                || (c.function() == ExprFunction.COALESCE && c.args().stream().anyMatch(this::temporal));
        default -> false;
        };
    }

    private static @Nullable Object literal(DslLiteral literal) {
        return switch (literal) {
        case TextLiteral t -> t.value();
        case NumberLiteral n -> n.value();
        case BoolLiteral b -> b.value();
        case NullLiteral n -> null;
        };
    }

    private CqlIdentifier column(FieldRef f) {
        CqlIdentifier column = columns.get(f.field().value());
        if (column == null) {
            throw new IllegalArgumentException("logical field '" + f.field().value() + "' has no column mapping");
        }
        return column;
    }
}
