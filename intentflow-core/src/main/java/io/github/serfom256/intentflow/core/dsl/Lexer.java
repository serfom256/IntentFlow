package io.github.serfom256.intentflow.core.dsl;

import io.github.serfom256.intentflow.core.ast.DslSource;

import java.util.List;

/**
 * Tokenizes a {@link DslSource} into a stream of {@link Token}s ending in an
 * {@link EofToken}. Throws {@code DslParseException} on malformed input.
 */
public interface Lexer {

    List<Token> tokenize(DslSource source);
}
