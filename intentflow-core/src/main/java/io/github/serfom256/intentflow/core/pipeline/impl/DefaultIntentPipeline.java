package io.github.serfom256.intentflow.core.pipeline.impl;

import io.github.serfom256.intentflow.core.api.AggregateRow;
import io.github.serfom256.intentflow.core.api.Aggregation;
import io.github.serfom256.intentflow.core.api.IntentRequest;
import io.github.serfom256.intentflow.core.api.IntentResponse;
import io.github.serfom256.intentflow.core.api.MicroFrontendUri;
import io.github.serfom256.intentflow.core.api.UserText;
import io.github.serfom256.intentflow.core.api.factories.IntentResponses;
import io.github.serfom256.intentflow.core.ast.Aggregate;
import io.github.serfom256.intentflow.core.ast.AstQuery;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.DslSource;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.ServiceNotFoundException;
import io.github.serfom256.intentflow.core.catalog.ServiceRelation;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.pipeline.AstValidator;
import io.github.serfom256.intentflow.core.pipeline.DslCache;
import io.github.serfom256.intentflow.core.pipeline.DslCacheKey;
import io.github.serfom256.intentflow.core.pipeline.DslParser;
import io.github.serfom256.intentflow.core.pipeline.IntentPipeline;
import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import io.github.serfom256.intentflow.core.pipeline.SqlBuilder;
import io.github.serfom256.intentflow.core.pipeline.TextNormalizer;
import io.github.serfom256.intentflow.core.pipeline.UriBuilder;
import io.github.serfom256.intentflow.core.pipeline.ValidatedQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public final class DefaultIntentPipeline implements IntentPipeline {

    private final TextNormalizer normalizer;
    private final DslCache cache;
    private final LlmClient llm;
    private final DslParser parser;
    private final AstValidator validator;
    private final SqlBuilder sqlBuilder;
    private final QueryExecutor executor;
    private final UriBuilder uriBuilder;
    private final ServiceCatalog catalog;

    @Override
    public IntentResponse<?> resolve(IntentRequest request) {
        return run(request, catalog, null);
    }


    @Override
    public <T> IntentResponse<T> resolve(IntentRequest request, Class<T> resultType) {
        ServiceDescriptor service = catalog.findByType(resultType).orElseThrow(() -> new IllegalArgumentException(
                "No IntentFlow service maps to " + resultType.getName()));
        List<ServiceDescriptor> scope = new ArrayList<>();
        scope.add(service);
        for (ServiceRelation relation : service.relations()) {
            catalog.find(relation.target()).filter(t -> !scope.contains(t)).ifPresent(scope::add);
        }
        return run(request, ServiceCatalogs.of(scope), service);
    }

    private <T> IntentResponse<T> run(IntentRequest request, ServiceCatalog scope,
                                      @Nullable ServiceDescriptor expected) {
        long start = System.nanoTime();
        UserText normalized = normalizer.normalize(request.text());
        DslCacheKey key = new DslCacheKey(normalized, expected == null ? null : expected.name());

        DslSource dsl = cache.get(key).orElse(null);
        boolean cached = dsl != null;
        if (cached) {
            log.debug("DSL cache hit for '{}': {}", normalized.value(), dsl.value());
        } else {
            long llmStart = System.nanoTime();
            dsl = llm.generateDsl(normalized, scope);
            log.debug("LLM translated '{}' in {} ms: {}", normalized.value(), millisSince(llmStart), dsl.value());
            cache.put(key, dsl);
        }

        AstQuery ast = parser.parse(dsl);
        if (expected != null && !ast.service().equals(expected.name())) {
            throw new ServiceNotFoundException(ast.service());
        }
        ValidatedQuery validated = validator.validate(ast, catalog);
        BoundQuery query = sqlBuilder.build(validated);
        log.debug("Query: {} binds={}", query.sql(), query.values());

        @SuppressWarnings("unchecked")
        Class<T> type = (Class<T>) validated.service().resultType();
        Aggregate aggregate = ast.aggregate();
        List<T> items = aggregate == null ? executor.execute(query, type) : List.of();
        Aggregation aggregation = aggregate == null ? null : aggregation(aggregate, executor.rows(query));
        MicroFrontendUri uri = uriBuilder.build(validated, items);
        log.debug("Resolved '{}' to {} with {} {} in {} ms", normalized.value(), uri.value(),
                aggregation == null ? items.size() : aggregation.rows().size(),
                aggregation == null ? type.getSimpleName() : "groups", millisSince(start));
        List<Object> parameters = new ArrayList<>(query.values());
        return IntentResponses.of(validated.service().name(), type, items, uri, aggregation,
                IntentResponses.trace(dsl.value(), cached, query.sql(), parameters));
    }

    private static Aggregation aggregation(Aggregate aggregate, List<List<@Nullable Object>> rows) {
        int groups = aggregate.groupBy().size();
        List<AggregateRow> out = new ArrayList<>(rows.size());
        for (List<@Nullable Object> row : rows) {
            if (row.size() != groups + 1) {
                throw new IllegalStateException("aggregate row has " + row.size() + " columns, expected "
                        + (groups + 1));
            }
            List<@Nullable Object> group = new ArrayList<>(groups);
            for (int i = 0; i < groups; i++) {
                group.add(AggregateValues.normalize(row.get(i)));
            }
            out.add(new AggregateRow(group, AggregateValues.normalize(row.get(groups))));
        }
        return new Aggregation(aggregate.function().keyword(),
                aggregate.field() == null ? null : aggregate.field().value(),
                aggregate.groupBy().stream().map(f -> f.value()).toList(), out);
    }

    private static long millisSince(long nanoStart) {
        return (System.nanoTime() - nanoStart) / 1_000_000;
    }
}
