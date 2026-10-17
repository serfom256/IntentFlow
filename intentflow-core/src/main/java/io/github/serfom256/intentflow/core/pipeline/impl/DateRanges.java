package io.github.serfom256.intentflow.core.pipeline.impl;

import io.github.serfom256.intentflow.core.ast.And;
import io.github.serfom256.intentflow.core.ast.Call;
import io.github.serfom256.intentflow.core.ast.Compare;
import io.github.serfom256.intentflow.core.ast.ComparisonOp;
import io.github.serfom256.intentflow.core.ast.DslLiteral;
import io.github.serfom256.intentflow.core.ast.ExprCompare;
import io.github.serfom256.intentflow.core.ast.ExprFunction;
import io.github.serfom256.intentflow.core.ast.ExprIn;
import io.github.serfom256.intentflow.core.ast.FieldRef;
import io.github.serfom256.intentflow.core.ast.FilterExpr;
import io.github.serfom256.intentflow.core.ast.LiteralExpr;
import io.github.serfom256.intentflow.core.ast.Not;
import io.github.serfom256.intentflow.core.ast.NumberLiteral;
import io.github.serfom256.intentflow.core.ast.Or;
import io.github.serfom256.intentflow.core.ast.TextLiteral;
import io.github.serfom256.intentflow.core.ast.ValueExpr;
import io.github.serfom256.intentflow.core.catalog.LogicalFieldName;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

final class DateRanges {

    private record Part(ExprFunction unit, LogicalFieldName field, int value) {
    }

    private DateRanges() {
    }

    static FilterExpr rewrite(FilterExpr expr, Predicate<LogicalFieldName> timestamp) {
        return switch (expr) {
            case And a -> conjunction(a, timestamp);
            case Or o -> {
                FilterExpr left = rewrite(o.left(), timestamp);
                FilterExpr right = rewrite(o.right(), timestamp);
                yield left == o.left() && right == o.right() ? o : new Or(left, right);
            }
            case Not n -> {
                FilterExpr inner = rewrite(n.inner(), timestamp);
                yield inner == n.inner() ? n : new Not(inner);
            }
            case ExprCompare c -> {
                FilterExpr range = single(c, timestamp);
                yield range == null ? c : range;
            }
            case ExprIn i -> {
                FilterExpr range = years(i, timestamp);
                yield range == null ? i : range;
            }
            default -> expr;
        };
    }

    private static FilterExpr conjunction(And and, Predicate<LogicalFieldName> timestamp) {
        List<FilterExpr> parts = new ArrayList<>();
        flatten(and, parts);
        Map<LogicalFieldName, Map<ExprFunction, Integer>> positions = new LinkedHashMap<>();
        for (int i = 0; i < parts.size(); i++) {
            Part part = equality(parts.get(i), timestamp);
            if (part != null) {
                positions.computeIfAbsent(part.field(), f -> new LinkedHashMap<>()).putIfAbsent(part.unit(), i);
            }
        }
        FilterExpr[] rewritten = parts.toArray(FilterExpr[]::new);
        boolean changed = false;
        for (Map.Entry<LogicalFieldName, Map<ExprFunction, Integer>> entry : positions.entrySet()) {
            Map<ExprFunction, Integer> units = entry.getValue();
            Integer year = units.get(ExprFunction.YEAR);
            if (year == null) {
                continue;
            }
            Integer month = units.get(ExprFunction.MONTH);
            Integer day = month == null ? null : units.get(ExprFunction.DAY);
            LocalDateTime[] range = calendarRange(value(parts.get(year)),
                    month == null ? null : value(parts.get(month)), day == null ? null : value(parts.get(day)));
            if (range == null) {
                continue;
            }
            rewritten[year] = range(entry.getKey(), range[0], range[1]);
            if (month != null) {
                rewritten[month] = null;
            }
            if (day != null) {
                rewritten[day] = null;
            }
            changed = true;
        }
        FilterExpr result = null;
        for (int i = 0; i < rewritten.length; i++) {
            if (rewritten[i] == null) {
                continue;
            }
            FilterExpr part = rewritten[i] == parts.get(i) ? rewrite(parts.get(i), timestamp) : rewritten[i];
            changed |= part != parts.get(i);
            result = result == null ? part : new And(result, part);
        }
        return changed ? result : and;
    }

    private static void flatten(FilterExpr expr, List<FilterExpr> out) {
        if (expr instanceof And a) {
            flatten(a.left(), out);
            flatten(a.right(), out);
        } else {
            out.add(expr);
        }
    }

    private static @Nullable Part equality(FilterExpr expr, Predicate<LogicalFieldName> timestamp) {
        if (!(expr instanceof ExprCompare c) || c.op() != ComparisonOp.EQ) {
            return null;
        }
        LogicalFieldName field = datePartField(c.left(), timestamp);
        Integer value = integer(c.right());
        if (field == null || value == null) {
            return null;
        }
        ExprFunction unit = ((Call) c.left()).function();
        return unit == ExprFunction.HOUR ? null : new Part(unit, field, value);
    }

    private static int value(FilterExpr part) {
        return integer(((ExprCompare) part).right());
    }

    private static @Nullable FilterExpr single(ExprCompare c, Predicate<LogicalFieldName> timestamp) {
        if (c.left() instanceof Call call && call.function() == ExprFunction.DATE_TRUNC) {
            return truncated(call, c, timestamp);
        }
        LogicalFieldName field = datePartField(c.left(), timestamp);
        Integer year = integer(c.right());
        if (field == null || year == null || ((Call) c.left()).function() != ExprFunction.YEAR) {
            return null;
        }
        LocalDateTime[] range = calendarRange(year, null, null);
        return range == null ? null : bounded(field, c.op(), range[0], range[1]);
    }

    private static @Nullable FilterExpr truncated(Call call, ExprCompare c, Predicate<LogicalFieldName> timestamp) {
        if (!(call.args().get(1) instanceof FieldRef f) || !timestamp.test(f.field())
                || !(call.args().getFirst() instanceof LiteralExpr unitLiteral)
                || !(unitLiteral.value() instanceof TextLiteral unitText)
                || !(c.right() instanceof LiteralExpr l) || !(l.value() instanceof TextLiteral t)) {
            return null;
        }
        LocalDateTime start = parse(t.value());
        String unit = unitText.value().toLowerCase(Locale.ROOT);
        if (start == null || !aligned(start, unit)) {
            return null;
        }
        LocalDateTime end = switch (unit) {
            case "year" -> start.plusYears(1);
            case "month" -> start.plusMonths(1);
            case "day" -> start.plusDays(1);
            case "hour" -> start.plusHours(1);
            default -> null;
        };
        return end == null ? null : bounded(f.field(), c.op(), start, end);
    }

    private static @Nullable FilterExpr years(ExprIn in, Predicate<LogicalFieldName> timestamp) {
        LogicalFieldName field = datePartField(in.value(), timestamp);
        if (field == null || ((Call) in.value()).function() != ExprFunction.YEAR) {
            return null;
        }
        FilterExpr result = null;
        for (DslLiteral literal : in.values()) {
            Integer year = integer(new LiteralExpr(literal));
            LocalDateTime[] range = year == null ? null : calendarRange(year, null, null);
            if (range == null) {
                return null;
            }
            FilterExpr part = range(field, range[0], range[1]);
            result = result == null ? part : new Or(result, part);
        }
        return result;
    }

    private static @Nullable FilterExpr bounded(LogicalFieldName field, ComparisonOp op, LocalDateTime start,
            LocalDateTime end) {
        return switch (op) {
            case EQ -> range(field, start, end);
            case NE -> new Or(compare(field, ComparisonOp.LT, start), compare(field, ComparisonOp.GE, end));
            case LT -> compare(field, ComparisonOp.LT, start);
            case LE -> compare(field, ComparisonOp.LT, end);
            case GT -> compare(field, ComparisonOp.GE, end);
            case GE -> compare(field, ComparisonOp.GE, start);
            case CONTAINS -> null;
        };
    }

    private static FilterExpr range(LogicalFieldName field, LocalDateTime start, LocalDateTime end) {
        return new And(compare(field, ComparisonOp.GE, start), compare(field, ComparisonOp.LT, end));
    }

    private static Compare compare(LogicalFieldName field, ComparisonOp op, LocalDateTime at) {
        String text = at.toLocalTime().equals(LocalTime.MIDNIGHT) ? at.toLocalDate().toString() : at.toString();
        return new Compare(field, op, new TextLiteral(text));
    }

    private static LocalDateTime @Nullable [] calendarRange(int year, @Nullable Integer month, @Nullable Integer day) {
        if (year < 1 || year > 9998) {
            return null;
        }
        if (month == null) {
            return new LocalDateTime[] { LocalDate.of(year, 1, 1).atStartOfDay(),
                    LocalDate.of(year + 1, 1, 1).atStartOfDay() };
        }
        if (month < 1 || month > 12) {
            return null;
        }
        YearMonth yearMonth = YearMonth.of(year, month);
        if (day == null) {
            return new LocalDateTime[] { yearMonth.atDay(1).atStartOfDay(),
                    yearMonth.plusMonths(1).atDay(1).atStartOfDay() };
        }
        if (day < 1 || day > yearMonth.lengthOfMonth()) {
            return null;
        }
        LocalDate date = yearMonth.atDay(day);
        return new LocalDateTime[] { date.atStartOfDay(), date.plusDays(1).atStartOfDay() };
    }

    private static @Nullable LogicalFieldName datePartField(ValueExpr value, Predicate<LogicalFieldName> timestamp) {
        if (value instanceof Call call
                && (call.function() == ExprFunction.YEAR || call.function() == ExprFunction.MONTH
                        || call.function() == ExprFunction.DAY || call.function() == ExprFunction.HOUR)
                && call.args().size() == 1 && call.args().getFirst() instanceof FieldRef f
                && timestamp.test(f.field())) {
            return f.field();
        }
        return null;
    }

    private static @Nullable Integer integer(ValueExpr value) {
        if (!(value instanceof LiteralExpr l) || !(l.value() instanceof NumberLiteral n)) {
            return null;
        }
        BigDecimal number = n.value().stripTrailingZeros();
        try {
            return number.scale() <= 0 ? number.intValueExact() : null;
        } catch (ArithmeticException e) {
            return null;
        }
    }

    private static @Nullable LocalDateTime parse(String text) {
        String value = text.strip();
        try {
            return value.length() == 10 ? LocalDate.parse(value).atStartOfDay()
                    : LocalDateTime.parse(value.replace(' ', 'T'));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static boolean aligned(LocalDateTime start, String unit) {
        return switch (unit) {
            case "year" -> start.getDayOfYear() == 1 && start.toLocalTime().equals(LocalTime.MIDNIGHT);
            case "month" -> start.getDayOfMonth() == 1 && start.toLocalTime().equals(LocalTime.MIDNIGHT);
            case "day" -> start.toLocalTime().equals(LocalTime.MIDNIGHT);
            case "hour" -> start.getMinute() == 0 && start.getSecond() == 0 && start.getNano() == 0;
            default -> false;
        };
    }
}
