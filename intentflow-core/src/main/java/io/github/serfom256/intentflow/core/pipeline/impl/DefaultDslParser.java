package io.github.serfom256.intentflow.core.pipeline.impl;

import io.github.serfom256.intentflow.core.ast.AstQuery;
import io.github.serfom256.intentflow.core.ast.DslSource;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.dsl.EofToken;
import io.github.serfom256.intentflow.core.dsl.Lexer;
import io.github.serfom256.intentflow.core.dsl.Parser;
import io.github.serfom256.intentflow.core.dsl.StringToken;
import io.github.serfom256.intentflow.core.dsl.Token;
import io.github.serfom256.intentflow.core.pipeline.DslParseException;
import io.github.serfom256.intentflow.core.pipeline.DslParser;
import io.github.serfom256.intentflow.core.pipeline.UnsupportedRequestException;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RequiredArgsConstructor
public final class DefaultDslParser implements DslParser {

    private static final Pattern UNSUPPORTED = Pattern.compile("(?is)\\s*unsupported\\b(.*)");

    private final Lexer lexer;
    private final Parser parser;

    @Override
    public AstQuery parse(DslSource dsl) {
        Matcher declined = UNSUPPORTED.matcher(dsl.value());
        if (declined.matches()) {
            throw new UnsupportedRequestException(reason(declined.group(1).strip()));
        }
        List<Token> tokens = lexer.tokenize(dsl);
        return parser.parse(tokens);
    }

    private String reason(String text) {
        if (text.isEmpty()) {
            return "no reason given";
        }
        try {
            List<Token> tokens = lexer.tokenize(DslSources.of(text));
            if (tokens.size() == 2 && tokens.getFirst() instanceof StringToken s && tokens.get(1) instanceof EofToken) {
                return s.value();
            }
        } catch (DslParseException e) {
            return text;
        }
        return text;
    }
}
