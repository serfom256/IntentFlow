package io.github.serfom256.intentflow.core.api.factories;

import io.github.serfom256.intentflow.core.api.Aggregation;
import io.github.serfom256.intentflow.core.api.IntentResponse;
import io.github.serfom256.intentflow.core.api.IntentTrace;
import io.github.serfom256.intentflow.core.api.MicroFrontendUri;
import io.github.serfom256.intentflow.core.api.impl.DefaultIntentResponse;
import io.github.serfom256.intentflow.core.api.impl.DefaultIntentTrace;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class IntentResponses {

    public static <T> IntentResponse<T> of(ServiceName service, Class<T> type, List<T> items, MicroFrontendUri uri) {
        return new DefaultIntentResponse<>(service, type, items, uri, null, trace("", false, "", List.of()));
    }

    public static <T> IntentResponse<T> of(ServiceName service, Class<T> type, List<T> items, MicroFrontendUri uri,
            @Nullable Aggregation aggregation, IntentTrace trace) {
        return new DefaultIntentResponse<>(service, type, items, uri, aggregation, trace);
    }

    public static IntentTrace trace(String dsl, boolean cached, String query, List<Object> parameters) {
        return new DefaultIntentTrace(dsl, cached, query, parameters);
    }

    public static IntentTrace trace(String dsl, boolean cached, String query, List<Object> parameters,
            List<String> optimizations) {
        return new DefaultIntentTrace(dsl, cached, query, parameters, optimizations);
    }

    public static IntentTrace trace(String dsl, boolean cached, String query, List<Object> parameters,
            List<String> optimizations, int repairs) {
        return new DefaultIntentTrace(dsl, cached, query, parameters, optimizations, repairs);
    }
}
