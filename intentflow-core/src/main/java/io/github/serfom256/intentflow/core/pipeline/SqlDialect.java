package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ast.CastType;
import io.github.serfom256.intentflow.core.ast.DslLiteral;
import io.github.serfom256.intentflow.core.ast.ExprFunction;
import io.github.serfom256.intentflow.core.catalog.SortKey;

/**
 * The database-specific parts of generated SQL. {@link SqlBuilder} owns the
 * query shape (WHERE tree, case-insensitive equality through {@code LOWER()})
 * and asks the dialect for everything that differs between engines.
 *
 * <p>
 * Every dialect must return the same rows for the same query:
 * <ul>
 * <li>{@code contains} is a case-insensitive substring match that takes the
 * text literally</li>
 * <li>sorting puts NULLs last in both directions</li>
 * <li>timestamp literals keep their local date and time; an offset is
 * ignored</li>
 * </ul>
 */
public interface SqlDialect {

    /** Short id, e.g. {@code postgresql}. */
    String name();

    String quote(String identifier);

    /**
     * Case-insensitive "column contains text"; {@code column} is already quoted.
     */
    void contains(SqlWriter sql, String column, String text);

    /** A bound text literal compared against a timestamp column. */
    void timestamp(SqlWriter sql, DslLiteral value);

    /**
     * One ORDER BY item, NULLs last; {@code value} writes the column or expression
     * and may be run more than once.
     */
    void orderBy(SqlWriter sql, Runnable value, SortKey.Direction direction);

    /**
     * A calendar part of a timestamp or date as a number: {@code YEAR},
     * {@code MONTH}, {@code DAY} or {@code HOUR}.
     */
    default void extract(SqlWriter sql, ExprFunction unit, Runnable value) {
        sql.text("EXTRACT(" + unit.name() + " FROM ");
        value.run();
        sql.text(")");
    }

    /**
     * A timestamp truncated to the start of its {@code year}, {@code month},
     * {@code day} or {@code hour}.
     */
    void truncate(SqlWriter sql, String unit, Runnable value);

    /** A value converted to another type, as in {@code '1234'::bigint}. */
    void cast(SqlWriter sql, CastType type, Runnable value);

    /** Number of characters of a text value. */
    default void length(SqlWriter sql, Runnable value) {
        sql.text("CHAR_LENGTH(");
        value.run();
        sql.text(")");
    }

    /**
     * A text value compared byte for byte, for case-sensitive fields. Databases
     * whose default collation ignores case,
     * like MySQL, override it; elsewhere the value is written as is.
     */
    default void exactText(SqlWriter sql, Runnable value) {
        value.run();
    }

    /** Row limit and offset; {@code offset} is 0 when there is nothing to skip. */
    void limit(SqlWriter sql, int limit, int offset);
}
