package io.github.serfom256.intentflow.core.api.factories;

import io.github.serfom256.intentflow.core.api.IntentRequest;
import io.github.serfom256.intentflow.core.api.UserText;
import io.github.serfom256.intentflow.core.api.impl.DefaultIntentRequest;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class IntentRequests {

    public static IntentRequest of(UserText text) {
        return new DefaultIntentRequest(text);
    }

    public static IntentRequest of(String text) {
        return of(UserTexts.of(text));
    }
}
