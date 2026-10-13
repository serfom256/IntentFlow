package io.github.serfom256.intentflow.dialect.mysql;

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
        sql.text("LOWER(").text(column).text(") LIKE LOWER(")
                .param(new TextLiteral(LikePatterns.containing(text, '!'))).text(") ESCAPE '!'");
    }

    @Override
    public void timestamp(SqlWriter sql, DslLiteral value) {
        DslLiteral bound = value instanceof TextLiteral t ? new TextLiteral(toSqlDateTime(t.value())) : value;
        sql.text("CAST(").param(bound).text(" AS DATETIME(6))");
    }

    @Override
    public void orderBy(SqlWriter sql, String column, SortKey.Direction direction) {
        sql.text(column).text(" IS NULL, ").text(column)
                .text(direction == SortKey.Direction.DESC ? " DESC" : " ASC");
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
