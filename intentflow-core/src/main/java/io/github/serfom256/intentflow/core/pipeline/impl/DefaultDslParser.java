package io.github.serfom256.intentflow.core.pipeline.impl;

import io.github.serfom256.intentflow.core.ast.AstQuery;
import io.github.serfom256.intentflow.core.ast.DslSource;
import io.github.serfom256.intentflow.core.dsl.Lexer;
import io.github.serfom256.intentflow.core.dsl.Parser;
import io.github.serfom256.intentflow.core.dsl.Token;
import io.github.serfom256.intentflow.core.pipeline.DslParser;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public final class DefaultDslParser implements DslParser {

    private final Lexer lexer;
    private final Parser parser;

    @Override
    public AstQuery parse(DslSource dsl) {
        List<Token> tokens = lexer.tokenize(dsl);
        return parser.parse(tokens);
    }
}
