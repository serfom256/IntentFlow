package io.github.serfom256.intentflow.core.api.factories;

import io.github.serfom256.intentflow.core.api.IntentContext;
import io.github.serfom256.intentflow.core.api.impl.DefaultIntentContext;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class IntentContexts {

    private static final IntentContext ANONYMOUS = new DefaultIntentContext(null, null, Set.of(), Map.of());

    public static IntentContext anonymous() {
        return ANONYMOUS;
    }

    public static IntentContext of(@Nullable String principal, @Nullable String tenant, Set<String> roles) {
        return new DefaultIntentContext(principal, tenant, roles, Map.of());
    }

    public static IntentContext of(@Nullable String principal, @Nullable String tenant, Set<String> roles,
            Map<String, Object> attributes) {
        return new DefaultIntentContext(principal, tenant, roles, attributes);
    }
}
