package io.github.serfom256.intentflow.core.dsl;

import io.github.serfom256.intentflow.core.ast.AstQuery;

import java.util.List;

/**
 * Parses a token stream into an {@link AstQuery}. Implementations must be
 * concurrency-safe — a single instance may be invoked by many threads at the
 * same time. The caller must not mutate the {@code tokens} list during the
 * call; {@link Lexer} returns an immutable list so the standard pipeline
 * satisfies this naturally.
 */
public interface Parser {

    AstQuery parse(List<Token> tokens);
}
