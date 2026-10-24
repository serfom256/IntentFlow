package io.github.serfom256.intentflow.dialect.oracle;

import io.github.serfom256.intentflow.core.ast.CastType;
import io.github.serfom256.intentflow.core.ast.DslLiteral;
import io.github.serfom256.intentflow.core.ast.ExprFunction;
import io.github.serfom256.intentflow.core.ast.TextLiteral;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import io.github.serfom256.intentflow.core.pipeline.SqlCapabilities;
import io.github.serfom256.intentflow.core.pipeline.SqlDialect;
import io.github.serfom256.intentflow.core.pipeline.SqlWriter;
import oracle.sql.Datum;
import org.jspecify.annotations.Nullable;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class OracleDialect implements SqlDialect {

    private static final SqlCapabilities CAPABILITIES = new SqlCapabilities(true, false, true, false, true, 1000,
            32_767);

    private static final Pattern FOLDED = Pattern.compile("[a-z][a-z0-9_]*|[A-Z][A-Z0-9_]*");

    private static final Set<String> RESERVED = Set.of(
            "ACCESS", "ADD", "ALL", "ALTER", "AND", "ANY", "AS", "ASC", "AUDIT", "BETWEEN", "BY", "CHAR", "CHECK",
            "CLUSTER", "COLUMN", "COLUMN_VALUE", "COMMENT", "COMPRESS", "CONNECT", "CREATE", "CURRENT", "DATE",
            "DECIMAL", "DEFAULT", "DELETE", "DESC", "DISTINCT", "DROP", "ELSE", "EXCLUSIVE", "EXISTS", "FILE",
            "FLOAT", "FOR", "FROM", "GRANT", "GROUP", "HAVING", "IDENTIFIED", "IMMEDIATE", "IN", "INCREMENT",
            "INDEX", "INITIAL", "INSERT", "INTEGER", "INTERSECT", "INTO", "IS", "LEVEL", "LIKE", "LOCK", "LONG",
            "MAXEXTENTS", "MINUS", "MLSLABEL", "MODE", "MODIFY", "NESTED_TABLE_ID", "NOAUDIT", "NOCOMPRESS", "NOT",
            "NOWAIT", "NULL", "NUMBER", "OF", "OFFLINE", "ON", "ONLINE", "OPTION", "OR", "ORDER", "PCTFREE",
            "PRIOR", "PUBLIC", "RAW", "RENAME", "RESOURCE", "REVOKE", "ROW", "ROWID", "ROWNUM", "ROWS", "SELECT",
            "SESSION", "SET", "SHARE", "SIZE", "SMALLINT", "START", "SUCCESSFUL", "SYNONYM", "SYSDATE", "TABLE",
            "THEN", "TO", "TRIGGER", "UID", "UNION", "UNIQUE", "UPDATE", "USER", "VALIDATE", "VALUES", "VARCHAR",
            "VARCHAR2", "VIEW", "WHENEVER", "WHERE", "WITH");

    private static final DateTimeFormatter SQL_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

    @Override
    public String name() {
        return "oracle";
    }

    @Override
    public String quote(String identifier) {
        if (FOLDED.matcher(identifier).matches() && !RESERVED.contains(identifier.toUpperCase(Locale.ROOT))) {
            return identifier;
        }
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    @Override
    public void contains(SqlWriter sql, String column, String text) {
        sql.text("LOWER(").text(column).text(") LIKE LOWER(")
                .param(new TextLiteral(LikePatterns.containing(text, '\\'))).text(") ESCAPE '\\'");
    }

    @Override
    public void literal(SqlWriter sql, DslLiteral value) {
        if (value instanceof TextLiteral text && text.value().isEmpty()) {
            sql.text("CHR(0)");
            return;
        }
        sql.param(value);
    }

    @Override
    public void timestamp(SqlWriter sql, DslLiteral value) {
        if (value instanceof TextLiteral t) {
            sql.text("TO_TIMESTAMP(").param(new TextLiteral(toSqlDateTime(t.value())))
                    .text(", 'YYYY-MM-DD HH24:MI:SS.FF6')");
            return;
        }
        sql.param(value);
    }

    @Override
    public void orderBy(SqlWriter sql, Runnable value, SortKey.Direction direction) {
        value.run();
        sql.text(direction == SortKey.Direction.DESC ? " DESC NULLS LAST" : " ASC NULLS LAST");
    }

    @Override
    public void extract(SqlWriter sql, ExprFunction unit, Runnable value) {
        sql.text("EXTRACT(" + unit.name() + " FROM CAST(");
        value.run();
        sql.text(" AS TIMESTAMP))");
    }

    @Override
    public void truncate(SqlWriter sql, String unit, Runnable value) {
        String format = switch (unit) {
            case "year" -> "YYYY";
            case "month" -> "MM";
            case "day" -> "DD";
            case "hour" -> "HH24";
            default -> throw new IllegalArgumentException("unsupported date_trunc unit: " + unit);
        };
        sql.text("CAST(TRUNC(");
        value.run();
        sql.text(", '" + format + "') AS TIMESTAMP)");
    }

    @Override
    public void inZone(SqlWriter sql, ZoneId zone, Runnable value) {
        sql.text("CAST((");
        value.run();
        sql.text(" AT TIME ZONE '" + zone.getId().replace("'", "''") + "') AS TIMESTAMP)");
    }

    @Override
    public void cast(SqlWriter sql, CastType type, Runnable value) {
        sql.text(type == CastType.DATE ? "TRUNC(CAST(" : "CAST(");
        value.run();
        sql.text(switch (type) {
            case NUMBER -> " AS NUMBER)";
            case INTEGER -> " AS NUMBER(19))";
            case TEXT -> " AS VARCHAR2(4000))";
            case TIMESTAMP -> " AS TIMESTAMP)";
            case DATE -> " AS DATE))";
        });
    }

    @Override
    public void length(SqlWriter sql, Runnable value) {
        sql.text("LENGTH(");
        value.run();
        sql.text(")");
    }

    @Override
    public @Nullable Object read(@Nullable Object value) {
        return value != null && value.getClass().getName().startsWith("oracle.sql.") ? DriverValues.jdbc(value)
                : value;
    }

    private static final class DriverValues {

        static Object jdbc(Object value) {
            try {
                return value instanceof Datum datum ? datum.toJdbc() : value;
            } catch (SQLException e) {
                throw new IllegalStateException("Could not read " + value.getClass().getName(), e);
            }
        }
    }

    @Override
    public SqlCapabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public void limit(SqlWriter sql, int limit, int offset) {
        if (offset > 0) {
            sql.text(" OFFSET " + offset + " ROWS");
        }
        sql.text(" FETCH NEXT " + limit + " ROWS ONLY");
    }

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
