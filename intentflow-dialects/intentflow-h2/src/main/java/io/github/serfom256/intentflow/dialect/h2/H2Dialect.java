package io.github.serfom256.intentflow.dialect.h2;

import io.github.serfom256.intentflow.core.ast.CastType;
import io.github.serfom256.intentflow.core.ast.DslLiteral;
import io.github.serfom256.intentflow.core.ast.NumberLiteral;
import io.github.serfom256.intentflow.core.ast.TextLiteral;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import io.github.serfom256.intentflow.core.pipeline.SqlDialect;
import io.github.serfom256.intentflow.core.pipeline.SqlWriter;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class H2Dialect implements SqlDialect {

    private static final Pattern FOLDED = Pattern.compile("[a-z_][a-z0-9_]*|[A-Z_][A-Z0-9_]*");

    private static final Set<String> KEYWORDS = Set.of(
            "ALL", "AND", "ANY", "ARRAY", "AS", "ASYMMETRIC", "AUTHORIZATION", "BETWEEN", "BOTH", "CASE", "CAST",
            "CHECK", "CONSTRAINT", "CROSS", "CURRENT_CATALOG", "CURRENT_DATE", "CURRENT_PATH", "CURRENT_ROLE",
            "CURRENT_SCHEMA", "CURRENT_TIME", "CURRENT_TIMESTAMP", "CURRENT_USER", "DAY", "DEFAULT", "DISTINCT",
            "ELSE", "END", "EXCEPT", "EXISTS", "FALSE", "FETCH", "FOR", "FOREIGN", "FROM", "FULL", "GROUP", "GROUPS",
            "HAVING", "HOUR", "IF", "ILIKE", "IN", "INNER", "INTERSECT", "INTERVAL", "IS", "JOIN", "KEY", "LEADING",
            "LEFT", "LIKE", "LIMIT", "LOCALTIME", "LOCALTIMESTAMP", "MINUS", "MINUTE", "MONTH", "NATURAL", "NOT",
            "NULL", "OFFSET", "ON", "OR", "ORDER", "OVER", "PARTITION", "PRIMARY", "QUALIFY", "RANGE", "REGEXP",
            "RIGHT", "ROW", "ROWNUM", "ROWS", "SECOND", "SELECT", "SESSION_USER", "SET", "SOME", "SYMMETRIC",
            "SYSTEM_USER", "TABLE", "TO", "TOP", "TRAILING", "TRUE", "UESCAPE", "UNION", "UNIQUE", "UNKNOWN", "USER",
            "USING", "VALUE", "VALUES", "WHEN", "WHERE", "WINDOW", "WITH", "YEAR", "_ROWID_");

    @Override
    public String name() {
        return "h2";
    }

    @Override
    public String quote(String identifier) {
        if (FOLDED.matcher(identifier).matches() && !KEYWORDS.contains(identifier.toUpperCase(Locale.ROOT))) {
            return identifier;
        }
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    @Override
    public void contains(SqlWriter sql, String column, String text) {
        sql.text(column).text(" ILIKE ").param(new TextLiteral(LikePatterns.containing(text, '\\')))
                .text(" ESCAPE '\\'");
    }

    @Override
    public void literal(SqlWriter sql, DslLiteral value) {
        BigDecimal exact = value instanceof NumberLiteral number ? number.value().stripTrailingZeros() : null;
        if (exact != null && exact.scale() > 0) {
            sql.text("CAST(").param(value)
                    .text(" AS NUMERIC(" + Math.max(exact.precision(), exact.scale()) + ", " + exact.scale() + "))");
        } else {
            sql.param(value);
        }
    }

    @Override
    public void timestamp(SqlWriter sql, DslLiteral value) {
        sql.text("CAST(").param(value).text(" AS TIMESTAMP)");
    }

    @Override
    public void orderBy(SqlWriter sql, Runnable value, SortKey.Direction direction) {
        value.run();
        sql.text(direction == SortKey.Direction.DESC ? " DESC NULLS LAST" : " ASC NULLS LAST");
    }

    @Override
    public void truncate(SqlWriter sql, String unit, Runnable value) {
        sql.text("DATE_TRUNC('" + unit + "', ");
        value.run();
        sql.text(")");
    }

    @Override
    public void inZone(SqlWriter sql, ZoneId zone, Runnable value) {
        sql.text("(");
        value.run();
        sql.text(" AT TIME ZONE '" + zone.getId().replace("'", "''") + "')");
    }

    @Override
    public void cast(SqlWriter sql, CastType type, Runnable value) {
        sql.text("CAST(");
        value.run();
        sql.text(switch (type) {
            case NUMBER -> " AS NUMERIC(38, 10))";
            case INTEGER -> " AS BIGINT)";
            case TEXT -> " AS VARCHAR)";
            case TIMESTAMP -> " AS TIMESTAMP)";
            case DATE -> " AS DATE)";
        });
    }

    @Override
    public void limit(SqlWriter sql, int limit, int offset) {
        if (offset > 0) {
            sql.text(" OFFSET " + offset + " ROWS");
        }
        sql.text(" FETCH FIRST " + limit + " ROWS ONLY");
    }
}
