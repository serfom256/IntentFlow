package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ast.CastType;
import io.github.serfom256.intentflow.core.ast.DslLiteral;
import io.github.serfom256.intentflow.core.ast.ExprFunction;
import io.github.serfom256.intentflow.core.ast.TextLiteral;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import org.jspecify.annotations.Nullable;

import java.time.ZoneId;

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

    /**
     * Case-insensitive "value contains text" for an expression, such as
     * {@code lower(name)} or the name of an enum stored as a number;
     * {@code value} writes it. The default uses {@code LIKE} with {@code '!'} as
     * the escape character.
     */
    default void contains(SqlWriter sql, Runnable value, String text) {
        StringBuilder pattern = new StringBuilder(text.length() + 2).append('%');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '!' || c == '%' || c == '_') {
                pattern.append('!');
            }
            pattern.append(c);
        }
        sql.text("LOWER(");
        value.run();
        sql.text(") LIKE LOWER(").param(new TextLiteral(pattern.append('%').toString())).text(") ESCAPE '!'");
    }

    /**
     * The name of a SQL function, such as {@code LOWER} or {@code SUM}, in this
     * database. The default returns it unchanged.
     */
    default String function(String name) {
        return name;
    }

    /**
     * A literal bound as a parameter. The default binds it as is. A database that
     * types a parameter after the other operand, like H2, which would turn
     * {@code 0.5} next to an integer column into {@code 1}, writes a cast that
     * keeps the literal's own type.
     */
    default void literal(SqlWriter sql, DslLiteral value) {
        sql.param(value);
    }

    /** A bound text literal compared against a timestamp column. */
    void timestamp(SqlWriter sql, DslLiteral value);

    /**
     * A timestamp the planner already converted to a Java value, such as the
     * bounds of a whole day: a {@code LocalDateTime}, or an
     * {@code OffsetDateTime} for a column that stores an instant. The default
     * binds it as is.
     */
    default void timestamp(SqlWriter sql, DslLiteral value, Object bound) {
        sql.param(value, bound);
    }

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

    /**
     * A timestamp that stores an instant, converted to the wall-clock time of
     * {@code zone} before a calendar part
     * is taken from it. The default leaves it unchanged, so the database session's
     * zone applies.
     */
    default void inZone(SqlWriter sql, ZoneId zone, Runnable value) {
        value.run();
    }

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

    /**
     * What the database supports. The planner checks it before building SQL, so a
     * query the database cannot run
     * fails with a clear {@code ValidationException} instead of an SQL error:
     * {@code top} needs window functions, sorting by a field of a related service
     * needs a correlated subquery in {@code ORDER BY}, grouping by a computed
     * value goes through a derived table when {@code GROUP BY} cannot name a
     * select-list alias, keyset paging and joins for sorting trust primary and
     * unique keys only where the database enforces them,
     * {@code in (...)} lists and the number of bound parameters are capped, and
     * without native booleans boolean
     * values are bound as 1 and 0.
     */
    default SqlCapabilities capabilities() {
        return SqlCapabilities.STANDARD;
    }

    /**
     * A value the executor read from a result row, as a standard JDBC type. The
     * default returns it unchanged; a driver with types of its own, like Oracle's
     * {@code oracle.sql.TIMESTAMP}, converts them here.
     */
    default @Nullable Object read(@Nullable Object value) {
        return value;
    }

    /** Row limit and offset; {@code offset} is 0 when there is nothing to skip. */
    void limit(SqlWriter sql, int limit, int offset);
}
