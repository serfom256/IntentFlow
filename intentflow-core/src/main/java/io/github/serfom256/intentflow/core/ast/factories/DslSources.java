package io.github.serfom256.intentflow.core.ast.factories;

import io.github.serfom256.intentflow.core.ast.DslSource;
import io.github.serfom256.intentflow.core.ast.impl.DefaultDslSource;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DslSources {

    public static DslSource of(String value) {
        return new DefaultDslSource(value);
    }
}
