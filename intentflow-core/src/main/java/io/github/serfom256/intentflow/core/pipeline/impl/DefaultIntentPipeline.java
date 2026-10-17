package io.github.serfom256.intentflow.core.pipeline.impl;

import io.github.serfom256.intentflow.core.api.AggregateRow;
import io.github.serfom256.intentflow.core.api.Aggregation;
import io.github.serfom256.intentflow.core.api.IntentRequest;
import io.github.serfom256.intentflow.core.api.IntentResponse;
import io.github.serfom256.intentflow.core.api.IntentTrace;
import io.github.serfom256.intentflow.core.api.MicroFrontendUri;
import io.github.serfom256.intentflow.core.api.UserText;
import io.github.serfom256.intentflow.core.api.factories.IntentResponses;
import io.github.serfom256.intentflow.core.ast.Aggregate;
import io.github.serfom256.intentflow.core.ast.AstQuery;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.DslSource;
import io.github.serfom256.intentflow.core.ast.Expressions;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.ServiceNotFoundException;
import io.github.serfom256.intentflow.core.catalog.ServiceRelation;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.pipeline.AstValidator;
import io.github.serfom256.intentflow.core.pipeline.DslCache;
import io.github.serfom256.intentflow.core.pipeline.DslCacheKey;
import io.github.serfom256.intentflow.core.pipeline.DslParseException;
import io.github.serfom256.intentflow.core.pipeline.DslParser;
import io.github.serfom256.intentflow.core.pipeline.IntentPipeline;
import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import io.github.serfom256.intentflow.core.pipeline.SqlBuilder;
import io.github.serfom256.intentflow.core.pipeline.TextNormalizer;
import io.github.serfom256.intentflow.core.pipeline.UnsupportedRequestException;
import io.github.serfom256.intentflow.core.pipeline.UriBuilder;
import io.github.serfom256.intentflow.core.pipeline.ValidatedQuery;
import io.github.serfom256.intentflow.core.pipeline.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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
    private final int maxRepairs;

    @Override
    public IntentResponse<?> resolve(IntentRequest request) {
        return execute(compile(request, catalog, null));
    }

    @Override
    public <T> IntentResponse<T> resolve(IntentRequest request, Class<T> resultType) {
        ServiceDescriptor service = serviceFor(resultType);
        return execute(compile(request, scopeOf(service), service));
    }

    @Override
    public IntentTrace explain(IntentRequest request) {
        return compile(request, catalog, null).trace();
    }

    @Override
    public IntentTrace explain(IntentRequest request, Class<?> resultType) {
        ServiceDescriptor service = serviceFor(resultType);
        return compile(request, scopeOf(service), service).trace();
    }

    private record Compiled(UserText question, DslSource dsl, boolean cached, ValidatedQuery validated,
            BoundQuery query, long startNanos, int repairs) {

        IntentTrace trace() {
            return IntentResponses.trace(dsl.value(), cached, query.sql(), new ArrayList<>(query.values()),
                    query.optimizations(), repairs);
        }
    }

    private Optional<List<BoundQuery>> related(Class<?> type, String relationName, Collection<?> keys) {
        return catalog.findByType(type)
                .flatMap(service -> service.relations().stream().filter(r -> r.name().equals(relationName)).findFirst())
                .flatMap(relation -> catalog.find(relation.target()).map(target -> {
                    String column = target.fields().stream().filter(f -> f.logical().equals(relation.to()))
                            .findFirst().map(f -> f.column().value()).orElse(relation.to().value());
                    return sqlBuilder.buildRelated(target, column, List.copyOf(keys), executor::restrictions);
                }));
    }

    private ServiceDescriptor serviceFor(Class<?> resultType) {
        return catalog.findByType(resultType).orElseThrow(() -> new IllegalArgumentException(
                "No IntentFlow service maps to " + resultType.getName()));
    }

    private ServiceCatalog scopeOf(ServiceDescriptor service) {
        List<ServiceDescriptor> scope = new ArrayList<>();
        scope.add(service);
        for (ServiceRelation relation : service.relations()) {
            catalog.find(relation.target()).filter(t -> !scope.contains(t)).ifPresent(scope::add);
        }
        return ServiceCatalogs.of(scope);
    }

    private Compiled compile(IntentRequest request, ServiceCatalog scope, @Nullable ServiceDescriptor expected) {
        long start = System.nanoTime();
        UserText normalized = normalizer.normalize(request.text());
        DslCacheKey key = new DslCacheKey(normalized, expected == null ? null : expected.name(), catalog.version());

        Optional<DslSource> hit = cache.get(key);
        boolean cached = hit.isPresent();
        DslSource dsl;
        if (cached) {
            dsl = hit.get();
            log.debug("DSL cache hit for '{}': {}", normalized.value(), dsl.value());
        } else {
            long llmStart = System.nanoTime();
            dsl = llm.generateDsl(normalized, scope);
            log.debug("LLM translated '{}' in {} ms: {}", normalized.value(), millisSince(llmStart), dsl.value());
        }

        int repairs = 0;
        ValidatedQuery validated;
        while (true) {
            try {
                validated = validate(dsl, expected);
                break;
            } catch (UnsupportedRequestException declined) {
                log.debug("Declined '{}': {}", normalized.value(), declined.getMessage());
                if (!cached) {
                    cache.put(key, dsl);
                }
                throw declined;
            } catch (DslParseException | ValidationException | ServiceNotFoundException rejected) {
                log.debug("Rejected DSL for '{}': {} ({})", normalized.value(), dsl.value(), rejected.getMessage());
                if (cached || repairs >= maxRepairs) {
                    throw rejected;
                }
                repairs++;
                long llmStart = System.nanoTime();
                dsl = llm.repairDsl(normalized, scope, dsl, rejected.getMessage());
                log.debug("LLM corrected '{}' in {} ms (attempt {} of {}): {}", normalized.value(),
                        millisSince(llmStart), repairs, maxRepairs, dsl.value());
            }
        }
        if (!cached) {
            cache.put(key, dsl);
        }
        BoundQuery query = sqlBuilder.build(validated, executor.shape(validated.service().resultType()),
                executor::restrictions);
        log.debug("Query: {} binds={}", query.sql(), query.values());
        return new Compiled(normalized, dsl, cached, validated, query, start, repairs);
    }

    private ValidatedQuery validate(DslSource dsl, @Nullable ServiceDescriptor expected) {
        AstQuery ast = parser.parse(dsl);
        if (expected != null && !ast.service().equals(expected.name())) {
            throw new ServiceNotFoundException(ast.service());
        }
        return validator.validate(ast, catalog);
    }

    private <T> IntentResponse<T> execute(Compiled compiled) {
        ValidatedQuery validated = compiled.validated();
        BoundQuery query = compiled.query();
        @SuppressWarnings("unchecked")
        Class<T> type = (Class<T>) validated.service().resultType();
        Aggregate aggregate = validated.ast().aggregate();
        List<T> items = aggregate == null ? executor.execute(query, type, this::related) : List.of();
        Aggregation aggregation = aggregate == null ? null : aggregation(aggregate, executor.rows(query));
        MicroFrontendUri uri = uriBuilder.build(validated, items);
        log.debug("Resolved '{}' to {} with {} {} in {} ms", compiled.question().value(), uri.value(),
                aggregation == null ? items.size() : aggregation.rows().size(),
                aggregation == null ? type.getSimpleName() : "groups", millisSince(compiled.startNanos()));
        return IntentResponses.of(validated.service().name(), type, items, uri, aggregation, compiled.trace());
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
                aggregate.argument() == null ? null : Expressions.print(aggregate.argument()),
                aggregate.groupNames(), out);
    }

    private static long millisSince(long nanoStart) {
        return (System.nanoTime() - nanoStart) / 1_000_000;
    }
}
