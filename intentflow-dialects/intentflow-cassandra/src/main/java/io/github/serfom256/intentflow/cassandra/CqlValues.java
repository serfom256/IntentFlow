package io.github.serfom256.intentflow.cassandra;

import com.datastax.oss.driver.api.core.type.DataType;
import com.datastax.oss.driver.api.core.type.DataTypes;
import io.github.serfom256.intentflow.core.ast.BoolLiteral;
import io.github.serfom256.intentflow.core.ast.DslLiteral;
import io.github.serfom256.intentflow.core.ast.NumberLiteral;
import io.github.serfom256.intentflow.core.ast.TextLiteral;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CqlValues {

    public static @Nullable Comparable<?> comparable(@Nullable Object value) {
        return switch (value) {
            case null -> null;
            case String s -> s;
            case BigDecimal n -> n;
            case BigInteger n -> new BigDecimal(n);
            case Integer n -> BigDecimal.valueOf(n);
            case Long n -> BigDecimal.valueOf(n);
            case Short n -> BigDecimal.valueOf(n);
            case Byte n -> BigDecimal.valueOf(n);
            case Double n -> Double.isFinite(n) ? BigDecimal.valueOf(n) : null;
            case Float n -> Float.isFinite(n) ? new BigDecimal(Float.toString(n)) : null;
            case Boolean b -> b;
            case Instant t -> LocalDateTime.ofInstant(t, ZoneOffset.UTC);
            case LocalDate d -> d.atStartOfDay();
            case LocalDateTime t -> t;
            default -> value.toString();
        };
    }

    static @Nullable Integer compare(Object column, DslLiteral literal, boolean equality, boolean caseSensitive) {
        return switch (comparable(column)) {
            case String s when literal instanceof TextLiteral t -> equality && !caseSensitive
                    ? lower(s).compareTo(lower(t.value()))
                    : s.compareTo(t.value());
            case BigDecimal n when literal instanceof NumberLiteral l -> n.compareTo(l.value());
            case Boolean b when literal instanceof BoolLiteral l -> Boolean.compare(b, l.value());
            case LocalDateTime d when literal instanceof TextLiteral t ->
                    parseTimestamp(t.value()).map(d::compareTo).orElse(null);
            case null, default -> null;
        };
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }

    static Optional<LocalDateTime> parseTimestamp(String text) {
        String s = text.strip().replace(' ', 'T');
        try {
            return Optional.of(LocalDate.parse(s).atStartOfDay());
        } catch (DateTimeParseException notDate) {
            try {
                return Optional.of(LocalDateTime.parse(s));
            } catch (DateTimeParseException notLocal) {
                try {
                    return Optional.of(OffsetDateTime.parse(s).toLocalDateTime());
                } catch (DateTimeParseException invalid) {
                    return Optional.empty();
                }
            }
        }
    }

    static boolean isText(DataType type) {
        return type.equals(DataTypes.TEXT) || type.equals(DataTypes.ASCII);
    }

    static @Nullable Object bindValue(DataType type, DslLiteral literal, boolean caseSensitive) {
        if (literal instanceof TextLiteral t && isText(type)) {
            return caseSensitive ? t.value() : null;
        }
        try {
            if (literal instanceof NumberLiteral n) {
                BigDecimal v = n.value();
                if (type.equals(DataTypes.DECIMAL)) {
                    return v;
                }
                if (type.equals(DataTypes.BIGINT)) {
                    return v.longValueExact();
                }
                if (type.equals(DataTypes.INT)) {
                    return v.intValueExact();
                }
                if (type.equals(DataTypes.SMALLINT)) {
                    return v.shortValueExact();
                }
                if (type.equals(DataTypes.TINYINT)) {
                    return v.byteValueExact();
                }
                if (type.equals(DataTypes.VARINT)) {
                    return v.toBigIntegerExact();
                }
                if (type.equals(DataTypes.DOUBLE)) {
                    double d = v.doubleValue();
                    return Double.isFinite(d) && BigDecimal.valueOf(d).compareTo(v) == 0 ? d : null;
                }
                return null;
            }
        } catch (ArithmeticException notExact) {
            return null;
        }
        if (literal instanceof BoolLiteral b && type.equals(DataTypes.BOOLEAN)) {
            return b.value();
        }
        if (literal instanceof TextLiteral t && type.equals(DataTypes.TIMESTAMP)) {
            return parseTimestamp(t.value()).map(d -> d.toInstant(ZoneOffset.UTC)).orElse(null);
        }
        return null;
    }
}
