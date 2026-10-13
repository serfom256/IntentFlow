package io.github.serfom256.intentflow.core.catalog.factories;

import io.github.serfom256.intentflow.core.catalog.MaxTake;
import io.github.serfom256.intentflow.core.catalog.impl.DefaultMaxTake;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MaxTakes {

    public static MaxTake of(int value) {
        return new DefaultMaxTake(value);
    }
}
