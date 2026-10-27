package io.github.serfom256.intentflow.core.pipeline.impl;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Timestamp;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class AggregateValues {

    static @Nullable Object normalize(@Nullable Object value) {
        return switch (value) {
        case null -> null;
        case BigDecimal n -> n;
        case BigInteger n -> new BigDecimal(n);
        case Double n -> Double.isFinite(n) ? BigDecimal.valueOf(n) : n;
        case Float n -> Float.isFinite(n) ? new BigDecimal(Float.toString(n)) : n;
        case Number n -> BigDecimal.valueOf(n.longValue());
        case Timestamp t -> t.toLocalDateTime();
        case Date d -> d.toLocalDate();
        default -> value;
        };
    }
}
