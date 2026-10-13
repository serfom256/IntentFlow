package io.github.serfom256.intentflow.core.pipeline.factories;

import io.github.serfom256.intentflow.core.pipeline.TextNormalizer;
import io.github.serfom256.intentflow.core.pipeline.impl.DefaultTextNormalizer;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TextNormalizers {

    public static TextNormalizer create() {
        return new DefaultTextNormalizer();
    }
}
