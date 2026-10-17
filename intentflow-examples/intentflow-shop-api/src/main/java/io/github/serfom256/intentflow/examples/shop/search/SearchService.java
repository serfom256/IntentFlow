package io.github.serfom256.intentflow.examples.shop.search;

import io.github.serfom256.intentflow.core.api.IntentResponse;
import io.github.serfom256.intentflow.core.api.IntentTrace;
import io.github.serfom256.intentflow.core.api.factories.IntentRequests;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceNames;
import io.github.serfom256.intentflow.core.pipeline.IntentPipeline;
import io.github.serfom256.intentflow.examples.shop.domain.Product;
import io.github.serfom256.intentflow.examples.shop.store.EntityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final IntentPipeline pipeline;
    private final ServiceCatalog catalog;
    private final EntityMapper mapper;

    public SearchResult<?> search(String query, boolean debug) {
        return timed(query, debug, () -> pipeline.resolve(IntentRequests.of(query)));
    }

    public SearchResult<?> search(String query, String service, boolean debug) {
        Class<?> type = catalog.get(ServiceNames.of(service)).resultType();
        return timed(query, debug, () -> pipeline.resolve(IntentRequests.of(query), type));
    }

    private SearchResult<?> timed(String query, boolean debug, Supplier<? extends IntentResponse<?>> resolve) {
        long start = System.nanoTime();
        IntentResponse<?> response = resolve.get();
        long tookMs = (System.nanoTime() - start) / 1_000_000;
        log.info("search '{}' -> {} ({} {}, {} ms)", query, response.uri().value(), response.items().size(),
                response.service().value(), tookMs);
        IntentTrace trace = response.trace();
        List<Object> items = response.items().stream().map(mapper::toDomain).map(SearchService::publicView).toList();
        return new SearchResult<>(query, response.service().value(), response.uri().value(), items,
                response.aggregation(),
                debug ? new SearchResult.Trace(trace.dsl(), trace.cached(), trace.query(), trace.parameters()) : null,
                tookMs);
    }

    private static Object publicView(Object item) {
        return item instanceof Product product ? ProductView.of(product) : item;
    }
}
