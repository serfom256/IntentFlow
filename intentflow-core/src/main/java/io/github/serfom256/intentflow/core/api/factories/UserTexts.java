package io.github.serfom256.intentflow.core.api.factories;

import io.github.serfom256.intentflow.core.api.UserText;
import io.github.serfom256.intentflow.core.api.impl.DefaultUserText;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UserTexts {

    public static UserText of(String value) {
        return new DefaultUserText(value);
    }
}
