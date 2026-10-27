package io.github.serfom256.intentflow.core.testing;

import io.github.serfom256.intentflow.core.pipeline.SqlDialect;

/**
 * A database the shared test suites run against. A new SQL dialect implements
 * this once, usually as a small enum or class backed by a Testcontainers
 * container, and passes it to {@link DialectConformanceTest}.
 */
public interface TestDatabase {

    record Endpoint(String url, String user, String password) {
    }

    /** Short name used in test output, e.g. {@code POSTGRES}. */
    String name();

    /** Starts the database on first use and returns how to connect to it. */
    Endpoint endpoint();

    /** The dialect under test. */
    SqlDialect dialect();

    /**
     * DDL for the {@code gadget} table: {@code id BIGINT PRIMARY KEY}, {@code name}
     * and {@code kind} as text with a binary (case-sensitive) collation,
     * {@code price DECIMAL(10,2)}, {@code stock} integer, {@code active} boolean
     * and {@code created_at} timestamp without time zone.
     */
    String createGadgetTable();

    /**
     * A {@code CREATE TABLE} written with the types PostgreSQL, MySQL and H2 share,
     * such as {@code BIGINT}, in this database's types. The default returns it
     * unchanged.
     */
    default String ddl(String createTable) {
        return createTable;
    }
}
