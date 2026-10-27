package io.github.serfom256.intentflow.dialect.mysql;

import io.github.serfom256.intentflow.core.ast.CastType;
import io.github.serfom256.intentflow.core.ast.DslLiteral;
import io.github.serfom256.intentflow.core.ast.TextLiteral;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import io.github.serfom256.intentflow.core.pipeline.SqlDialect;
import io.github.serfom256.intentflow.core.pipeline.SqlWriter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class MySqlDialect implements SqlDialect {

    @Override
    public String name() {
        return "mysql";
    }

    @Override
    public String quote(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    @Override
    public void contains(SqlWriter sql, String column, String text) {
        sql.text("LOWER(").text(column).text(") LIKE LOWER(").param(new TextLiteral(LikePatterns.containing(text, '!')))
                .text(") ESCAPE '!'");
    }

    @Override
    public void timestamp(SqlWriter sql, DslLiteral value) {
        DslLiteral bound = value instanceof TextLiteral t ? new TextLiteral(toSqlDateTime(t.value())) : value;
        sql.text("CAST(").param(bound).text(" AS DATETIME(6))");
    }

    @Override
    public void orderBy(SqlWriter sql, Runnable value, SortKey.Direction direction) {
        value.run();
        sql.text(" IS NULL, ");
        value.run();
        sql.text(direction == SortKey.Direction.DESC ? " DESC" : " ASC");
    }

    @Override
    public void truncate(SqlWriter sql, String unit, Runnable value) {
        if (unit.equals("day")) {
            sql.text("CAST(DATE(");
            value.run();
            sql.text(") AS DATETIME(6))");
            return;
        }
        String format = switch (unit) {
        case "year" -> "%Y-01-01";
        case "month" -> "%Y-%m-01";
        case "hour" -> "%Y-%m-%d %H:00:00";
        default -> throw new IllegalArgumentException("unsupported date_trunc unit: " + unit);
        };
        sql.text("CAST(DATE_FORMAT(");
        value.run();
        sql.text(", '" + format + "') AS DATETIME(6))");
    }

    @Override
    public void exactText(SqlWriter sql, Runnable value) {
        sql.text("CAST(");
        value.run();
        sql.text(" AS BINARY)");
    }

    @Override
    public void cast(SqlWriter sql, CastType type, Runnable value) {
        sql.text("CAST(");
        value.run();
        sql.text(switch (type) {
        case NUMBER -> " AS DECIMAL(38, 10))";
        case INTEGER -> " AS SIGNED)";
        case TEXT -> " AS CHAR)";
        case TIMESTAMP -> " AS DATETIME(6))";
        case DATE -> " AS DATE)";
        });
    }

    @Override
    public void limit(SqlWriter sql, int limit, int offset) {
        sql.text(" LIMIT " + limit);
        if (offset > 0) {
            sql.text(" OFFSET " + offset);
        }
    }

    private static final DateTimeFormatter SQL_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

    private static String toSqlDateTime(String text) {
        String s = text.strip().replace(' ', 'T');
        LocalDateTime local;
        try {
            local = LocalDate.parse(s).atStartOfDay();
        } catch (DateTimeParseException notDate) {
            try {
                local = LocalDateTime.parse(s);
            } catch (DateTimeParseException notLocal) {
                local = OffsetDateTime.parse(s).toLocalDateTime();
            }
        }
        return SQL_DATE_TIME.format(local);
    }
}
