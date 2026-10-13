package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ast.DslLiteral;
import io.github.serfom256.intentflow.core.catalog.SortKey;

/**
 * The database-specific parts of generated SQL. {@link SqlBuilder} owns the
 * query shape (WHERE tree, case-insensitive equality through {@code LOWER()})
 * and asks the dialect for everything that differs between engines.
 *
 * <p>Every dialect must return the same rows for the same query:
 * <ul>
 *   <li>{@code contains} is a case-insensitive substring match that takes the text literally</li>
 *   <li>sorting puts NULLs last in both directions</li>
 *   <li>timestamp literals keep their local date and time; an offset is ignored</li>
 * </ul>
 */
public interface SqlDialect {

    /** Short id, e.g. {@code postgresql}. */
    String name();

    String quote(String identifier);

    /** Case-insensitive "column contains text"; {@code column} is already quoted. */
    void contains(SqlWriter sql, String column, String text);

    /** A bound text literal compared against a timestamp column. */
    void timestamp(SqlWriter sql, DslLiteral value);

    /** One ORDER BY item, NULLs last; {@code column} is already quoted. */
    void orderBy(SqlWriter sql, String column, SortKey.Direction direction);

    /** Row limit and offset; {@code offset} is 0 when there is nothing to skip. */
    void limit(SqlWriter sql, int limit, int offset);
}
