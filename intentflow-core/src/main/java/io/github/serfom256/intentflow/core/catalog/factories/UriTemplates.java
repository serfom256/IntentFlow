package io.github.serfom256.intentflow.core.catalog.factories;

import io.github.serfom256.intentflow.core.catalog.UriTemplate;
import io.github.serfom256.intentflow.core.catalog.impl.DefaultUriTemplate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UriTemplates {

    public static UriTemplate of(String value) {
        return new DefaultUriTemplate(value);
    }
}
