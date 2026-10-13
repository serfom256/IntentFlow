package io.github.serfom256.intentflow.core.pipeline.factories;

import io.github.serfom256.intentflow.core.dsl.Lexer;
import io.github.serfom256.intentflow.core.dsl.Parser;
import io.github.serfom256.intentflow.core.dsl.factories.Lexers;
import io.github.serfom256.intentflow.core.dsl.factories.Parsers;
import io.github.serfom256.intentflow.core.pipeline.DslParser;
import io.github.serfom256.intentflow.core.pipeline.impl.DefaultDslParser;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DslParsers {

    public static DslParser create() {
        return new DefaultDslParser(Lexers.create(), Parsers.create());
    }

    public static DslParser create(Lexer lexer, Parser parser) {
        return new DefaultDslParser(lexer, parser);
    }
}
