package io.github.serfom256.intentflow.core.ast.factories;

import io.github.serfom256.intentflow.core.ast.Skip;
import io.github.serfom256.intentflow.core.ast.impl.DefaultSkip;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Skips {

    public static Skip of(int value) {
        return new DefaultSkip(value);
    }
}
