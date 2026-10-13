package io.github.serfom256.intentflow.core.dsl.factories;

import io.github.serfom256.intentflow.core.dsl.Position;
import io.github.serfom256.intentflow.core.dsl.impl.DefaultPosition;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Positions {

    public static Position of(int offset, int line, int column) {
        return new DefaultPosition(offset, line, column);
    }
}
