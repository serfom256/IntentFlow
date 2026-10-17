package io.github.serfom256.intentflow.core.pipeline.factories;

import io.github.serfom256.intentflow.core.pipeline.AstValidator;
import io.github.serfom256.intentflow.core.pipeline.impl.DefaultAstValidator;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AstValidators {

    public static final int DEFAULT_MAX_SKIP = 10_000;

    public static AstValidator create() {
        return create(DEFAULT_MAX_SKIP);
    }

    public static AstValidator create(int maxSkip) {
        return new DefaultAstValidator(maxSkip);
    }
}
