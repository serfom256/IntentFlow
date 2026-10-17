package io.github.serfom256.intentflow.core.pipeline.impl;

import io.github.serfom256.intentflow.core.ast.Arith;
import io.github.serfom256.intentflow.core.ast.BoolLiteral;
import io.github.serfom256.intentflow.core.ast.Call;
import io.github.serfom256.intentflow.core.ast.CastExpr;
import io.github.serfom256.intentflow.core.ast.CastType;
import io.github.serfom256.intentflow.core.ast.ExprFunction;
import io.github.serfom256.intentflow.core.ast.Expressions;
import io.github.serfom256.intentflow.core.ast.FieldRef;
import io.github.serfom256.intentflow.core.ast.LiteralExpr;
import io.github.serfom256.intentflow.core.ast.NullLiteral;
import io.github.serfom256.intentflow.core.ast.NumberLiteral;
import io.github.serfom256.intentflow.core.ast.TextLiteral;
import io.github.serfom256.intentflow.core.ast.ValueExpr;
import io.github.serfom256.intentflow.core.catalog.FieldMapping;
import io.github.serfom256.intentflow.core.pipeline.ValidationException;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

final class ExprTypes {

    enum Type {
        TEXT, NUMBER, BOOLEAN, TIMESTAMP, DATE, ANY, NULL
    }

    static final Set<String> TRUNCATE_UNITS = Set.of("year", "month", "day", "hour");
    private static final int MAX_NODES = 40;

    private final Function<String, FieldMapping> fields;

    ExprTypes(Function<String, FieldMapping> fields) {
        this.fields = fields;
    }

    ExprTypes(Map<String, FieldMapping> fields) {
        this(name -> {
            FieldMapping fm = fields.get(name);
            if (fm == null) {
                throw new ValidationException("field '" + name + "' is not declared or not queryable");
            }
            return fm;
        });
    }

    Type check(ValueExpr expr) {
        if (size(expr) > MAX_NODES) {
            throw new ValidationException("expression is too large: " + Expressions.print(expr));
        }
        return type(expr);
    }

    Type type(ValueExpr expr) {
        return switch (expr) {
            case FieldRef f -> {
                if (f.field().value().contains(".")) {
                    throw new ValidationException("related field '" + f.field().value()
                            + "' cannot be used inside an expression");
                }
                yield of(fields.apply(f.field().value()));
            }
            case LiteralExpr l -> switch (l.value()) {
                case TextLiteral t -> Type.TEXT;
                case NumberLiteral n -> Type.NUMBER;
                case BoolLiteral b -> Type.BOOLEAN;
                case NullLiteral n -> Type.NULL;
            };
            case Call c -> call(c);
            case Arith a -> {
                require(a.left(), Type.NUMBER, "arithmetic");
                require(a.right(), Type.NUMBER, "arithmetic");
                yield Type.NUMBER;
            }
            case CastExpr c -> {
                Type source = type(c.value());
                boolean ok = switch (c.type()) {
                    case NUMBER, INTEGER -> is(source, Type.NUMBER, Type.TEXT);
                    case TEXT -> is(source, Type.TEXT, Type.NUMBER);
                    case TIMESTAMP, DATE -> is(source, Type.TEXT, Type.TIMESTAMP, Type.DATE);
                };
                if (!ok) {
                    throw new ValidationException("cannot cast " + name(source) + " to " + c.type().keyword()
                            + " in " + Expressions.print(c));
                }
                if (c.value() instanceof LiteralExpr l && l.value() instanceof TextLiteral t
                        && (c.type() == CastType.TIMESTAMP
                                || c.type() == CastType.DATE)
                        && !Timestamps.isValid(t.value())) {
                    throw new ValidationException("'" + t.value() + "' is not a date or timestamp");
                }
                yield switch (c.type()) {
                    case NUMBER, INTEGER -> Type.NUMBER;
                    case TEXT -> Type.TEXT;
                    case TIMESTAMP -> Type.TIMESTAMP;
                    case DATE -> Type.DATE;
                };
            }
        };
    }

    private Type call(Call c) {
        return switch (c.function()) {
            case COALESCE -> coalesce(c);
            case YEAR, MONTH, DAY, HOUR -> unary(c, Type.NUMBER, Type.TIMESTAMP, Type.DATE);
            case DATE_TRUNC -> dateTrunc(c);
            case LENGTH -> unary(c, Type.NUMBER, Type.TEXT);
            case LOWER, UPPER -> unary(c, Type.TEXT, Type.TEXT);
            case ROUND -> round(c);
            case ABS -> unary(c, Type.NUMBER, Type.NUMBER);
        };
    }

    private Type unary(Call c, Type result, Type argument, Type... alternatives) {
        exactly(c, 1);
        require(c.args().getFirst(), argument, c.function().keyword(), alternatives);
        return result;
    }

    private Type coalesce(Call c) {
        if (c.args().size() < 2) {
            throw arity(c.function(), "at least 2");
        }
        Type result = Type.NULL;
        for (ValueExpr arg : c.args()) {
            result = common(result, type(arg), arg, c);
        }
        return result == Type.NULL ? Type.ANY : result;
    }

    private Type dateTrunc(Call c) {
        exactly(c, 2);
        if (!(c.args().getFirst() instanceof LiteralExpr l) || !(l.value() instanceof TextLiteral unit)
                || !TRUNCATE_UNITS.contains(unit.value().toLowerCase(Locale.ROOT))) {
            throw new ValidationException("date_trunc takes \"year\", \"month\", \"day\" or \"hour\" first");
        }
        require(c.args().get(1), Type.TIMESTAMP, c.function().keyword(), Type.DATE);
        return Type.TIMESTAMP;
    }

    private Type round(Call c) {
        int arity = c.args().size();
        if (arity != 1 && arity != 2) {
            throw arity(c.function(), "1 or 2");
        }
        require(c.args().getFirst(), Type.NUMBER, c.function().keyword());
        if (arity == 2 && !(c.args().get(1) instanceof LiteralExpr l && l.value() instanceof NumberLiteral n
                && digits(n) >= 0)) {
            throw new ValidationException("round takes a whole number of digits from 0 to 6 second");
        }
        return Type.NUMBER;
    }

    static int digits(NumberLiteral n) {
        try {
            int d = n.value().intValueExact();
            return d >= 0 && d <= 6 ? d : -1;
        } catch (ArithmeticException e) {
            return -1;
        }
    }

    private Type common(Type a, Type b, ValueExpr arg, Call c) {
        if (a == Type.NULL || a == Type.ANY)
            return b == Type.NULL ? a : b;
        if (b == Type.NULL || b == Type.ANY || a == b)
            return a;
        if ((a == Type.TIMESTAMP || a == Type.DATE) && b == Type.TEXT && literalTimestamp(arg))
            return a;
        throw new ValidationException("coalesce mixes " + name(a) + " and " + name(b) + " in " + Expressions.print(c));
    }

    static boolean literalTimestamp(ValueExpr e) {
        return e instanceof LiteralExpr l && l.value() instanceof TextLiteral t && Timestamps.isValid(t.value());
    }

    private void require(ValueExpr arg, Type wanted, String where, Type... alternatives) {
        Type actual = type(arg);
        if (actual == Type.ANY || actual == wanted || Set.of(alternatives).contains(actual)) {
            return;
        }
        throw new ValidationException(where + " needs a " + name(wanted) + ", got " + name(actual) + " in "
                + Expressions.print(arg));
    }

    private static void exactly(Call c, int n) {
        if (c.args().size() != n) {
            throw arity(c.function(), String.valueOf(n));
        }
    }

    private static ValidationException arity(ExprFunction fn, String expected) {
        return new ValidationException(fn.keyword() + " takes " + expected + " argument(s)");
    }

    private static boolean is(Type t, Type... allowed) {
        return t == Type.ANY || Set.of(allowed).contains(t);
    }

    static Type of(FieldMapping fm) {
        return switch (fm.type()) {
            case TEXT -> Type.TEXT;
            case NUMBER -> Type.NUMBER;
            case BOOLEAN -> Type.BOOLEAN;
            case TIMESTAMP -> Type.TIMESTAMP;
            case ANY -> Type.ANY;
        };
    }

    static String name(Type t) {
        return t.name().toLowerCase(Locale.ROOT);
    }

    private static int size(ValueExpr e) {
        return switch (e) {
            case FieldRef f -> 1;
            case LiteralExpr l -> 1;
            case Call c -> 1 + c.args().stream().mapToInt(ExprTypes::size).sum();
            case Arith a -> 1 + size(a.left()) + size(a.right());
            case CastExpr c -> 1 + size(c.value());
        };
    }
}
