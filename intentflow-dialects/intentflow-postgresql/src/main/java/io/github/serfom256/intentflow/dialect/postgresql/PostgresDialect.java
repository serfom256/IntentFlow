package io.github.serfom256.intentflow.dialect.postgresql;

import io.github.serfom256.intentflow.core.ast.DslLiteral;
import io.github.serfom256.intentflow.core.ast.TextLiteral;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import io.github.serfom256.intentflow.core.pipeline.SqlDialect;
import io.github.serfom256.intentflow.core.pipeline.SqlWriter;

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
    public void orderBy(SqlWriter sql, String column, SortKey.Direction direction) {
        sql.text(column).text(direction == SortKey.Direction.DESC ? " DESC NULLS LAST" : " ASC");
    }

    @Override
    public void limit(SqlWriter sql, int limit, int offset) {
        sql.text(" LIMIT " + limit);
        if (offset > 0) {
            sql.text(" OFFSET " + offset);
        }
    }
}
