package io.github.serfom256.intentflow.core.ast.factories;

import io.github.serfom256.intentflow.core.ast.Take;
import io.github.serfom256.intentflow.core.ast.impl.DefaultTake;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Takes {

    public static Take of(int value) {
        return new DefaultTake(value);
    }
}
