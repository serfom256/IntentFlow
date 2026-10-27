package io.github.serfom256.intentflow.core.api.factories;

import io.github.serfom256.intentflow.core.api.IntentContext;
import io.github.serfom256.intentflow.core.api.IntentRequest;
import io.github.serfom256.intentflow.core.api.UserText;
import io.github.serfom256.intentflow.core.api.impl.DefaultIntentRequest;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class IntentRequests {

    public static IntentRequest of(UserText text) {
        return new DefaultIntentRequest(text);
    }

    public static IntentRequest of(String text) {
        return of(UserTexts.of(text));
    }

    public static IntentRequest of(UserText text, IntentContext context) {
        return new DefaultIntentRequest(text, context);
    }

    public static IntentRequest of(String text, IntentContext context) {
        return of(UserTexts.of(text), context);
    }

    public static IntentRequest page(String text, @Nullable String cursor) {
        return new DefaultIntentRequest(UserTexts.of(text), Optional.empty(), true, Optional.ofNullable(cursor));
    }

    public static IntentRequest page(String text, IntentContext context, @Nullable String cursor) {
        return new DefaultIntentRequest(UserTexts.of(text), Optional.of(context), true, Optional.ofNullable(cursor));
    }

    public static IntentRequest withContext(IntentRequest request, IntentContext context) {
        return new DefaultIntentRequest(request.text(), Optional.of(context), request.paged(), request.cursor());
    }
}
