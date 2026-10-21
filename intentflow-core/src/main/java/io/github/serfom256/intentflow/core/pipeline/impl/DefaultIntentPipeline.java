package io.github.serfom256.intentflow.core.pipeline.impl;

import io.github.serfom256.intentflow.core.api.AggregateRow;
import io.github.serfom256.intentflow.core.api.Aggregation;
import io.github.serfom256.intentflow.core.api.IntentContext;
import io.github.serfom256.intentflow.core.api.IntentRequest;
import io.github.serfom256.intentflow.core.api.IntentResponse;
import io.github.serfom256.intentflow.core.api.IntentTrace;
import io.github.serfom256.intentflow.core.api.MicroFrontendUri;
import io.github.serfom256.intentflow.core.api.TokenUsage;
import io.github.serfom256.intentflow.core.api.UserText;
import io.github.serfom256.intentflow.core.api.factories.IntentResponses;
import io.github.serfom256.intentflow.core.ast.Aggregate;
import io.github.serfom256.intentflow.core.ast.AstQuery;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.DslSource;
import io.github.serfom256.intentflow.core.ast.Expressions;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.catalog.ServiceNotFoundException;
import io.github.serfom256.intentflow.core.catalog.ServiceRelation;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.pipeline.AccessDeniedException;
import io.github.serfom256.intentflow.core.pipeline.AccessPolicy;
import io.github.serfom256.intentflow.core.pipeline.AstValidator;
import io.github.serfom256.intentflow.core.pipeline.AuditLog;
import io.github.serfom256.intentflow.core.pipeline.DslCache;
import io.github.serfom256.intentflow.core.pipeline.DslCacheKey;
import io.github.serfom256.intentflow.core.pipeline.DslParseException;
import io.github.serfom256.intentflow.core.pipeline.DslParser;
import io.github.serfom256.intentflow.core.pipeline.IntentPipeline;
import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import io.github.serfom256.intentflow.core.pipeline.LlmRateLimiter;
import io.github.serfom256.intentflow.core.pipeline.PipelineListener.Outcome;
import io.github.serfom256.intentflow.core.pipeline.PipelineListener;
import io.github.serfom256.intentflow.core.pipeline.PipelineTracer.Span;
import io.github.serfom256.intentflow.core.pipeline.PipelineTracer.Stage;
import io.github.serfom256.intentflow.core.pipeline.PipelineTracer;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import io.github.serfom256.intentflow.core.pipeline.QuestionAudit;
import io.github.serfom256.intentflow.core.pipeline.RateLimitExceededException;
import io.github.serfom256.intentflow.core.pipeline.RuntimeRestriction;
import io.github.serfom256.intentflow.core.pipeline.SqlBuilder;
import io.github.serfom256.intentflow.core.pipeline.TextNormalizer;
import io.github.serfom256.intentflow.core.pipeline.UnsupportedRequestException;
import io.github.serfom256.intentflow.core.pipeline.UriBuilder;
import io.github.serfom256.intentflow.core.pipeline.ValidatedQuery;
import io.github.serfom256.intentflow.core.pipeline.ValidationException;
import io.github.serfom256.intentflow.core.pipeline.factories.ValidatedQueries;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

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
    private final AccessPolicy policy;
    private final Supplier<IntentContext> defaultCaller;
    private final PipelineListener listener;
    private final PipelineTracer tracer;
    private final LlmRateLimiter rateLimiter;
    private final AuditLog audit;
    private final boolean logValues;
    private final boolean visibleFieldsOnly;

    @Override
    public IntentResponse<?> resolve(IntentRequest request) {
        long start = System.nanoTime();
        Trail trail = new Trail();
        return observed(start, null, request, trail, () -> {
            Caller caller = caller(request, trail);
            return execute(compile(request, caller, caller.view(), null, trail), caller, trail);
        });
    }

    @Override
    public <T> IntentResponse<T> resolve(IntentRequest request, Class<T> resultType) {
        long start = System.nanoTime();
        ServiceDescriptor service = serviceFor(resultType);
        Trail trail = new Trail();
        return observed(start, service.name(), request, trail, () -> {
            Caller caller = caller(request, trail);
            ServiceDescriptor own = permitted(service, caller);
            return execute(compile(request, caller, scopeOf(own, caller.view()), own, trail), caller, trail);
        });
    }

    @Override
    public IntentTrace explain(IntentRequest request) {
        Trail trail = new Trail();
        Caller caller = caller(request, trail);
        return compile(request, caller, caller.view(), null, trail).trace();
    }

    @Override
    public IntentTrace explain(IntentRequest request, Class<?> resultType) {
        Trail trail = new Trail();
        Caller caller = caller(request, trail);
        ServiceDescriptor own = permitted(serviceFor(resultType), caller);
        return compile(request, caller, scopeOf(own, caller.view()), own, trail).trace();
    }

    private record Caller(IntentContext context, ServiceCatalog view,
            Function<Class<?>, List<RuntimeRestriction>> restrictions) {
    }

    private record Compiled(UserText question, DslSource dsl, boolean cached, ValidatedQuery validated,
            BoundQuery query, long startNanos, int repairs, TokenUsage tokens) {

        IntentTrace trace() {
            return IntentResponses.trace(dsl.value(), cached, query.sql(), new ArrayList<>(query.values()),
                    query.optimizations(), repairs, tokens);
        }
    }

    private static final class Trail {

        private @Nullable IntentContext caller;
        private @Nullable String dsl;
        private boolean cached;
        private @Nullable BoundQuery query;
        private int rows;
        private TokenUsage tokens = TokenUsage.NONE;
    }

    private Caller caller(IntentRequest request, Trail trail) {
        IntentContext context = request.context() != null ? request.context() : defaultCaller.get();
        trail.caller = context;
        if (policy == AccessPolicy.ALLOW_ALL) {
            return new Caller(context, catalog, executor::restrictions);
        }
        return new Caller(context, AccessView.of(catalog, policy, context), type -> {
            List<RuntimeRestriction> restrictions = new ArrayList<>(executor.restrictions(type));
            catalog.findByType(type).ifPresent(service -> restrictions.addAll(policy.restrictions(service, context)));
            return restrictions;
        });
    }

    private ServiceDescriptor permitted(ServiceDescriptor service, Caller caller) {
        return caller.view().find(service.name()).orElseThrow(() -> new AccessDeniedException(
                "The caller may not query " + service.name().value()));
    }

    private <R> R observed(long start, @Nullable ServiceName service, IntentRequest request, Trail trail,
            Supplier<R> resolve) {
        Span span = tracer.start(Stage.REQUEST);
        try {
            R response = resolve.get();
            ended(span, start, response instanceof IntentResponse<?> r ? r.service() : service, Outcome.ANSWERED,
                    null, request, trail);
            return response;
        } catch (UnsupportedRequestException e) {
            ended(span, start, service, Outcome.DECLINED, e, request, trail);
            throw e;
        } catch (DslParseException | ValidationException | ServiceNotFoundException e) {
            ended(span, start, service, Outcome.REJECTED, e, request, trail);
            throw e;
        } catch (AccessDeniedException e) {
            ended(span, start, service, Outcome.DENIED, e, request, trail);
            throw e;
        } catch (RateLimitExceededException e) {
            ended(span, start, service, Outcome.LIMITED, e, request, trail);
            throw e;
        } catch (RuntimeException e) {
            ended(span, start, service, Outcome.FAILED, e, request, trail);
            throw e;
        }
    }

    private void ended(Span span, long start, @Nullable ServiceName service, Outcome outcome,
            @Nullable RuntimeException cause, IntentRequest request, Trail trail) {
        Duration elapsed = since(start);
        listener.request(service, outcome, elapsed);
        if (!trail.tokens.equals(TokenUsage.NONE)) {
            span.usage(trail.tokens, null);
        }
        span.end(service, outcome.name().toLowerCase(Locale.ROOT), outcome == Outcome.FAILED ? cause : null);
        if (audit == AuditLog.NONE) {
            return;
        }
        try {
            IntentContext who = trail.caller != null ? trail.caller : request.context();
            BoundQuery query = trail.query;
            audit.question(new QuestionAudit(Instant.now(), who == null ? null : who.principal(),
                    who == null ? null : who.tenant(), request.text().value(), service, outcome, trail.dsl,
                    trail.cached, query == null ? null : query.sql(), query == null ? List.of() : query.values(),
                    trail.rows, trail.tokens, elapsed, cause == null ? null : cause.getMessage()));
        } catch (RuntimeException e) {
            log.warn("IntentFlow audit log {} failed", audit.getClass().getName(), e);
        }
    }

    private <R> R traced(Stage stage, @Nullable ServiceName service, Supplier<R> work) {
        Span span = tracer.start(stage);
        try {
            R result = work.get();
            if (result instanceof DslSource dsl && !dsl.usage().equals(TokenUsage.NONE)) {
                span.usage(dsl.usage(), dsl.model());
            }
            span.end(service, "ok", null);
            return result;
        } catch (UnsupportedRequestException e) {
            span.end(service, "declined", null);
            throw e;
        } catch (DslParseException | ValidationException | ServiceNotFoundException e) {
            span.end(service, "rejected", e);
            throw e;
        } catch (RuntimeException e) {
            span.end(service, "error", e);
            throw e;
        }
    }

    private Optional<List<BoundQuery>> related(Class<?> type, String relationName, Collection<?> keys,
            Function<Class<?>, List<RuntimeRestriction>> restrictions) {
        return catalog.findByType(type)
                .flatMap(service -> service.relations().stream().filter(r -> r.name().equals(relationName)).findFirst())
                .flatMap(relation -> catalog.find(relation.target()).map(target -> {
                    String column = target.fields().stream().filter(f -> f.logical().equals(relation.to()))
                            .findFirst().map(f -> f.column().value()).orElse(relation.to().value());
                    return sqlBuilder.buildRelated(target, column, List.copyOf(keys), restrictions);
                }));
    }

    private ServiceDescriptor serviceFor(Class<?> resultType) {
        return catalog.findByType(resultType).orElseThrow(() -> new IllegalArgumentException(
                "No IntentFlow service maps to " + resultType.getName()));
    }

    private static ServiceCatalog scopeOf(ServiceDescriptor service, ServiceCatalog view) {
        List<ServiceDescriptor> scope = new ArrayList<>();
        scope.add(service);
        for (ServiceRelation relation : service.relations()) {
            view.find(relation.target()).filter(t -> !scope.contains(t)).ifPresent(scope::add);
        }
        return ServiceCatalogs.of(scope);
    }

    private Compiled compile(IntentRequest request, Caller caller, ServiceCatalog scope,
            @Nullable ServiceDescriptor expected, Trail trail) {
        long start = System.nanoTime();
        ServiceName expectedName = expected == null ? null : expected.name();
        UserText normalized = normalizer.normalize(request.text());
        DslCacheKey key = new DslCacheKey(normalized, expectedName, caller.view().version());

        Optional<DslSource> hit = cache.get(key);
        boolean cached = hit.isPresent();
        trail.cached = cached;
        listener.cache(cached);
        DslSource dsl;
        TokenUsage tokens = TokenUsage.NONE;
        if (cached) {
            dsl = hit.get();
            trail.dsl = dsl.value();
            log.debug("DSL cache hit for '{}': {}", text(normalized.value()), dsl(dsl.value()));
        } else {
            rateLimiter.acquire(caller.context());
            long llmStart = System.nanoTime();
            dsl = askLlm(false, expectedName, () -> llm.generateDsl(normalized, scope));
            tokens = dsl.usage();
            trail.dsl = dsl.value();
            trail.tokens = tokens;
            log.debug("LLM translated '{}' in {} ms: {}", text(normalized.value()), millisSince(llmStart),
                    dsl(dsl.value()));
        }

        int repairs = 0;
        ValidatedQuery validated;
        while (true) {
            try {
                DslSource current = dsl;
                validated = traced(Stage.VALIDATE, expectedName, () -> validate(current, expected, caller.view()));
                break;
            } catch (UnsupportedRequestException declined) {
                log.debug("Declined '{}': {}", text(normalized.value()), text(declined.getMessage()));
                if (!cached) {
                    cache.put(key, dsl);
                }
                throw declined;
            } catch (DslParseException | ValidationException | ServiceNotFoundException rejected) {
                log.debug("Rejected DSL for '{}': {} ({})", text(normalized.value()), dsl(dsl.value()),
                        text(rejected.getMessage()));
                if (cached || repairs >= maxRepairs) {
                    throw rejected;
                }
                repairs++;
                long llmStart = System.nanoTime();
                DslSource failed = dsl;
                dsl = askLlm(true, expectedName,
                        () -> llm.repairDsl(normalized, scope, failed, rejected.getMessage()));
                tokens = tokens.plus(dsl.usage());
                trail.dsl = dsl.value();
                trail.tokens = tokens;
                log.debug("LLM corrected '{}' in {} ms (attempt {} of {}): {}", text(normalized.value()),
                        millisSince(llmStart), repairs, maxRepairs, dsl(dsl.value()));
            }
        }
        if (!cached) {
            cache.put(key, dsl);
        }
        ValidatedQuery valid = validated;
        BoundQuery query = traced(Stage.BUILD, valid.service().name(),
                () -> sqlBuilder.build(valid, executor.shape(valid.service().resultType()), caller.restrictions()));
        trail.query = query;
        log.debug("Query: {} binds={}", query.sql(), LogValues.values(logValues, query.values()));
        return new Compiled(normalized, dsl, cached, validated, query, start, repairs, tokens);
    }

    private DslSource askLlm(boolean repair, @Nullable ServiceName service, Supplier<DslSource> call) {
        long start = System.nanoTime();
        try {
            DslSource dsl = traced(repair ? Stage.REPAIR : Stage.GENERATE, service, call);
            listener.llm(repair, since(start), false);
            if (!dsl.usage().equals(TokenUsage.NONE)) {
                listener.tokens(dsl.model(), repair, dsl.usage());
            }
            return dsl;
        } catch (RuntimeException e) {
            listener.llm(repair, since(start), true);
            throw e;
        }
    }

    private ValidatedQuery validate(DslSource dsl, @Nullable ServiceDescriptor expected, ServiceCatalog view) {
        AstQuery ast = parser.parse(dsl);
        if (expected != null && !ast.service().equals(expected.name())) {
            throw new ServiceNotFoundException(ast.service());
        }
        ValidatedQuery validated = validator.validate(ast, view);
        if (view == catalog) {
            return validated;
        }
        Map<String, ServiceDescriptor> related = new LinkedHashMap<>();
        validated.related().forEach((name, service) -> related.put(name, catalog.get(service.name())));
        return ValidatedQueries.of(catalog.get(validated.service().name()), validated.ast(), related);
    }

    private <T> IntentResponse<T> execute(Compiled compiled, Caller caller, Trail trail) {
        ValidatedQuery validated = compiled.validated();
        BoundQuery query = compiled.query();
        @SuppressWarnings("unchecked")
        Class<T> type = (Class<T>) validated.service().resultType();
        Aggregate aggregate = validated.ast().aggregate();
        long start = System.nanoTime();
        Span span = tracer.start(Stage.QUERY);
        List<T> items;
        Aggregation aggregation;
        try {
            items = aggregate == null
                    ? executor.execute(query, type, (t, relation, keys) -> related(t, relation, keys,
                            caller.restrictions()))
                    : List.of();
            aggregation = aggregate == null ? null : aggregation(aggregate, executor.rows(query));
        } catch (RuntimeException e) {
            listener.query(validated.service().name(), aggregate != null, since(start), 0, true);
            span.end(validated.service().name(), "error", e);
            throw e;
        }
        int rows = aggregation == null ? items.size() : aggregation.rows().size();
        trail.rows = rows;
        listener.query(validated.service().name(), aggregate != null, since(start), rows, false);
        span.end(validated.service().name(), "ok", null);
        MicroFrontendUri uri = uriBuilder.build(validated, items);
        if (visibleFieldsOnly) {
            items = VisibleEntities.of(items, validated.service(), caller.view());
        }
        log.debug("Resolved '{}' to {} with {} {} in {} ms", text(compiled.question().value()), uri.value(), rows,
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

    private Object text(@Nullable String text) {
        return text == null ? "" : LogValues.text(logValues, text);
    }

    private Object dsl(String dsl) {
        return LogValues.dsl(logValues, dsl);
    }

    private static Duration since(long nanoStart) {
        return Duration.ofNanos(System.nanoTime() - nanoStart);
    }

    private static long millisSince(long nanoStart) {
        return (System.nanoTime() - nanoStart) / 1_000_000;
    }
}
