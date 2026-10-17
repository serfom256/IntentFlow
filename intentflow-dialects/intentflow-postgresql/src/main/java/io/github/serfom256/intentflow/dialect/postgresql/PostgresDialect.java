package io.github.serfom256.intentflow.dialect.postgresql;

import io.github.serfom256.intentflow.core.ast.CastType;
import io.github.serfom256.intentflow.core.ast.DslLiteral;
import io.github.serfom256.intentflow.core.ast.TextLiteral;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import io.github.serfom256.intentflow.core.pipeline.SqlDialect;
import io.github.serfom256.intentflow.core.pipeline.SqlWriter;

import java.time.ZoneId;

public final class PostgresDialect implements SqlDialect {

    @Override
    public String name() {
        return "postgresql";
    }

    @Override
    public String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    @Override
    public void contains(SqlWriter sql, String column, String text) {
        sql.text(column).text(" ILIKE ").param(new TextLiteral(LikePatterns.containing(text, '\\')))
                .text(" ESCAPE '\\'");
    }

    @Override
    public void timestamp(SqlWriter sql, DslLiteral value) {
        sql.text("CAST(").param(value).text(" AS TIMESTAMP)");
    }

    @Override
    public void orderBy(SqlWriter sql, Runnable value, SortKey.Direction direction) {
        value.run();
        sql.text(direction == SortKey.Direction.DESC ? " DESC NULLS LAST" : " ASC");
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
            case NUMBER -> " AS NUMERIC)";
            case INTEGER -> " AS BIGINT)";
            case TEXT -> " AS TEXT)";
            case TIMESTAMP -> " AS TIMESTAMP)";
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
}
