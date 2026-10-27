package io.github.serfom256.intentflow.core.ast.factories;

import io.github.serfom256.intentflow.core.api.TokenUsage;
import io.github.serfom256.intentflow.core.ast.DslSource;
import io.github.serfom256.intentflow.core.ast.impl.DefaultDslSource;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DslSources {

    public static DslSource of(String value) {
        return new DefaultDslSource(value);
    }

    public static DslSource of(String value, TokenUsage usage, @Nullable String model) {
        return new DefaultDslSource(value, usage, Optional.ofNullable(model));
    }
}
