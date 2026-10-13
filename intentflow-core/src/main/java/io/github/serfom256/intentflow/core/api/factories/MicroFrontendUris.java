package io.github.serfom256.intentflow.core.api.factories;

import io.github.serfom256.intentflow.core.api.MicroFrontendUri;
import io.github.serfom256.intentflow.core.api.impl.DefaultMicroFrontendUri;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MicroFrontendUris {

    public static MicroFrontendUri of(String value) {
        return new DefaultMicroFrontendUri(value);
    }
}
