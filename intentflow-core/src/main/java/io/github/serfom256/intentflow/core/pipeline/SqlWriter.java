package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ast.DslLiteral;

/** Append-only SQL buffer a {@link SqlDialect} writes its fragments into. */
public interface SqlWriter {

    SqlWriter text(String text);

    /** Appends a bound parameter; the executor decides the placeholder syntax. */
    SqlWriter param(DslLiteral value);

    /**
     * Appends a bound parameter whose value is a typed Java object, such as an
     * {@code OffsetDateTime};
     * {@code display} is how the parameter appears in traces.
     */
    default SqlWriter param(DslLiteral display, Object value) {
        return param(display);
    }
}
