package io.github.serfom256.intentflow.core.schema;

import io.github.serfom256.intentflow.core.catalog.ServiceName;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * What is known about the physical tables behind the catalog: keys, NOT NULL
 * columns and exact stored values per
 * table, and per service the restriction every row must satisfy. The SQL
 * planner only optimizes on known facts; an
 * unknown table yields {@link TableFacts#UNKNOWN}, which keeps the conservative
 * SQL.
 */
public interface SchemaFacts {

    /**
     * Facts of {@code table} as named in the catalog, e.g. {@code orders} or
     * {@code shop.orders}.
     */
    TableFacts table(String table);

    /**
     * SQL condition every row of {@code service} satisfies, such as an ORM
     * soft-delete condition, or {@code null}.
     * It is per service rather than per table, because several services may map one
     * table with different rules.
     */
    default @Nullable String restriction(ServiceName service) {
        return null;
    }

    /**
     * No facts: every column may be NULL, no key is unique, no restriction applies.
     */
    static SchemaFacts none() {
        return table -> TableFacts.UNKNOWN;
    }

    /** Facts for the given tables; table names are matched ignoring case. */
    static SchemaFacts of(Map<String, TableFacts> tables) {
        return of(tables, Map.of());
    }

    /** Facts for the given tables and restrictions of the given services. */
    static SchemaFacts of(Map<String, TableFacts> tables, Map<ServiceName, String> restrictions) {
        Map<String, TableFacts> byName = tables.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                e -> e.getKey().toLowerCase(Locale.ROOT), Map.Entry::getValue, TableFacts::merge));
        Map<ServiceName, String> byService = Map.copyOf(restrictions);
        return new SchemaFacts() {
            @Override
            public TableFacts table(String table) {
                return byName.getOrDefault(table.toLowerCase(Locale.ROOT), TableFacts.UNKNOWN);
            }

            @Override
            public @Nullable String restriction(ServiceName service) {
                return byService.get(service);
            }
        };
    }
}
