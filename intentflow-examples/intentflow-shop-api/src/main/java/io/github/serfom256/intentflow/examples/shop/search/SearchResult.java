package io.github.serfom256.intentflow.examples.shop.search;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.serfom256.intentflow.core.api.Aggregation;
import org.jspecify.annotations.Nullable;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchResult<T>(String query, String service, String uri, List<T> items,
        @Nullable Aggregation aggregation, @Nullable Trace trace, long tookMs) {

    public record Trace(String dsl, boolean cached, String sql, List<Object> parameters) {
    }
}
