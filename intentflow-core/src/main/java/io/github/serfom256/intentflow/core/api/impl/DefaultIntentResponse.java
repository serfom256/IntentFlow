package io.github.serfom256.intentflow.core.api.impl;

import io.github.serfom256.intentflow.core.api.Aggregation;
import io.github.serfom256.intentflow.core.api.IntentResponse;
import io.github.serfom256.intentflow.core.api.IntentTrace;
import io.github.serfom256.intentflow.core.api.MicroFrontendUri;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public record DefaultIntentResponse<T>(ServiceName service, Class<T> type, List<T> items, MicroFrontendUri uri,
        Optional<Aggregation> aggregation, IntentTrace trace, Optional<String> nextCursor)
        implements IntentResponse<T> {

    public DefaultIntentResponse(ServiceName service, Class<T> type, List<T> items, MicroFrontendUri uri,
            @Nullable Aggregation aggregation, IntentTrace trace) {
        this(service, type, items, uri, Optional.ofNullable(aggregation), trace, Optional.empty());
    }

    public DefaultIntentResponse {
        items = List.copyOf(items);
        for (T item : items) {
            if (!type.isInstance(item)) {
                throw new IllegalArgumentException(
                        "item of " + item.getClass().getName() + " is not a " + type.getName());
            }
        }
    }
}
