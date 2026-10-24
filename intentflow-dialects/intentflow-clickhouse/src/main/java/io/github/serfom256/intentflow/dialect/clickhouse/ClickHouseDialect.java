package io.github.serfom256.intentflow.dialect.clickhouse;

import io.github.serfom256.intentflow.core.ast.CastType;
import io.github.serfom256.intentflow.core.ast.DslLiteral;
import io.github.serfom256.intentflow.core.ast.NumberLiteral;
import io.github.serfom256.intentflow.core.ast.TextLiteral;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import io.github.serfom256.intentflow.core.pipeline.SqlCapabilities;
import io.github.serfom256.intentflow.core.pipeline.SqlDialect;
import io.github.serfom256.intentflow.core.pipeline.SqlWriter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class ClickHouseDialect implements SqlDialect {

    private static final SqlCapabilities CAPABILITIES = new SqlCapabilities(true, true, false, true, false,
            Integer.MAX_VALUE, 65_535);

    private static final DateTimeFormatter SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter MICROS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

    @Override
    public String name() {
        return "clickhouse";
    }

    @Override
    public String quote(String identifier) {
        return "\"" + identifier.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    @Override
    public void contains(SqlWriter sql, String column, String text) {
        sql.text("positionCaseInsensitiveUTF8(").text(column).text(", ").param(new TextLiteral(text)).text(") > 0");
    }

    @Override
    public void contains(SqlWriter sql, Runnable value, String text) {
        sql.text("positionCaseInsensitiveUTF8(");
        value.run();
        sql.text(", ").param(new TextLiteral(text)).text(") > 0");
    }

    @Override
    public String function(String name) {
        return switch (name) {
            case "LOWER" -> "lowerUTF8";
            case "UPPER" -> "upperUTF8";
            case "AVG" -> "avgOrNull";
            case "SUM" -> "sumOrNull";
            case "MIN" -> "minOrNull";
            case "MAX" -> "maxOrNull";
            default -> name;
        };
    }

    @Override
    public void literal(SqlWriter sql, DslLiteral value) {
        if (!(value instanceof NumberLiteral number)) {
            sql.param(value);
        } else if (number.value().stripTrailingZeros().scale() > 0) {
            sql.text("CAST(").param(new TextLiteral(number.value().toPlainString())).text(" AS Decimal(38, 10))");
        } else {
            sql.param(new NumberLiteral(new BigDecimal(number.value().toBigIntegerExact())));
        }
    }

    @Override
    public void timestamp(SqlWriter sql, DslLiteral value) {
        if (!(value instanceof TextLiteral text)) {
            sql.param(value);
            return;
        }
        String iso = text.value().strip().replace(' ', 'T');
        try {
            OffsetDateTime instant = OffsetDateTime.parse(iso);
            sql.text("parseDateTime64BestEffort(").param(new TextLiteral(instant.toString())).text(", 6)");
        } catch (DateTimeParseException local) {
            sql.param(new TextLiteral(text(localDateTime(iso))));
        }
    }

    @Override
    public void timestamp(SqlWriter sql, DslLiteral value, Object bound) {
        switch (bound) {
            case LocalDateTime local -> sql.param(new TextLiteral(text(local)));
            case OffsetDateTime instant -> sql.text("parseDateTime64BestEffort(")
                    .param(new TextLiteral(instant.toString())).text(", 6)");
            default -> sql.param(value, bound);
        }
    }

    @Override
    public void orderBy(SqlWriter sql, Runnable value, SortKey.Direction direction) {
        value.run();
        sql.text(direction == SortKey.Direction.DESC ? " DESC NULLS LAST" : " ASC NULLS LAST");
    }

    @Override
    public void truncate(SqlWriter sql, String unit, Runnable value) {
        sql.text("CAST(dateTrunc('" + unit + "', ");
        value.run();
        sql.text(") AS Nullable(DateTime64(6)))");
    }

    @Override
    public void inZone(SqlWriter sql, ZoneId zone, Runnable value) {
        sql.text("toTimeZone(");
        value.run();
        sql.text(", '" + zone.getId().replace("'", "\\'") + "')");
    }

    @Override
    public void cast(SqlWriter sql, CastType type, Runnable value) {
        if (type == CastType.INTEGER) {
            sql.text("CAST(round(CAST(");
            value.run();
            sql.text(" AS " + type(CastType.NUMBER) + ")) AS " + type(type) + ")");
            return;
        }
        sql.text("CAST(");
        value.run();
        sql.text(" AS " + type(type) + ")");
    }

    private static String type(CastType type) {
        return "Nullable(" + switch (type) {
            case NUMBER -> "Decimal(38, 10)";
            case INTEGER -> "Int64";
            case TEXT -> "String";
            case TIMESTAMP -> "DateTime64(6)";
            case DATE -> "Date";
        } + ")";
    }

    @Override
    public void length(SqlWriter sql, Runnable value) {
        sql.text("lengthUTF8(");
        value.run();
        sql.text(")");
    }

    @Override
    public SqlCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public void limit(SqlWriter sql, int limit, int offset) {
        sql.text(" LIMIT " + limit);
        if (offset > 0) {
            sql.text(" OFFSET " + offset);
        }
    }

    private static String text(LocalDateTime local) {
        return (local.getNano() == 0 ? SECONDS : MICROS).format(local);
    }

    private static LocalDateTime localDateTime(String iso) {
        try {
            return LocalDate.parse(iso).atStartOfDay();
        } catch (DateTimeParseException notDate) {
            return LocalDateTime.parse(iso);
        }
    }
}
