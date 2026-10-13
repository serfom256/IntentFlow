package io.github.serfom256.intentflow.core.dsl;

public interface Position {

    /** Zero-based character offset into the {@code DslSource}. */
    int offset();

    /** One-based line number (starts at 1). */
    int line();

    /** One-based column number within the line (starts at 1). */
    int column();
}
