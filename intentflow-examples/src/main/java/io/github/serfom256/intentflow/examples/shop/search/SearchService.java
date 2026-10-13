package io.github.serfom256.intentflow.examples.shop.search;

import io.github.serfom256.intentflow.core.api.IntentResponse;
import io.github.serfom256.intentflow.core.api.IntentTrace;
import io.github.serfom256.intentflow.core.api.factories.IntentRequests;
import io.github.serfom256.intentflow.core.pipeline.IntentPipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final IntentPipeline pipeline;

    public SearchResult<?> search(String query, boolean debug) {
        return timed(query, debug, () -> pipeline.resolve(IntentRequests.of(query)));
    }

    public <T> SearchResult<T> search(String query, Class<T> type, boolean debug) {
        return timed(query, debug, () -> pipeline.resolve(IntentRequests.of(query), type));
    }

    private <T> SearchResult<T> timed(String query, boolean debug, Supplier<IntentResponse<T>> resolve) {
        long start = System.nanoTime();
        IntentResponse<T> response = resolve.get();
        long tookMs = (System.nanoTime() - start) / 1_000_000;
        log.info("search '{}' -> {} ({} {}, {} ms)", query, response.uri().value(), response.items().size(),
                response.service().value(), tookMs);
        IntentTrace trace = response.trace();
        return new SearchResult<>(query, response.service().value(), response.uri().value(), response.items(),
                response.aggregation(),
                debug ? new SearchResult.Trace(trace.dsl(), trace.cached(), trace.query(), trace.parameters()) : null,
                tookMs);
    }
}
