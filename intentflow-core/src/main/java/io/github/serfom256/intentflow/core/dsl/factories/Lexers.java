package io.github.serfom256.intentflow.core.dsl.factories;

import io.github.serfom256.intentflow.core.dsl.Lexer;
import io.github.serfom256.intentflow.core.dsl.impl.DefaultLexer;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Lexers {

    public static Lexer create() {
        return new DefaultLexer();
    }
}
