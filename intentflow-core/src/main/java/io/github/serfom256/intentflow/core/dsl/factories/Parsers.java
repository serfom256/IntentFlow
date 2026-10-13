package io.github.serfom256.intentflow.core.dsl.factories;

import io.github.serfom256.intentflow.core.dsl.Parser;
import io.github.serfom256.intentflow.core.dsl.impl.DefaultParser;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Parsers {

    public static Parser create() {
        return new DefaultParser();
    }
}
