package io.github.serfom256.intentflow.core.pipeline.factories;

import io.github.serfom256.intentflow.core.api.IntentContext;
import io.github.serfom256.intentflow.core.api.factories.IntentContexts;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.pipeline.AccessPolicy;
import io.github.serfom256.intentflow.core.pipeline.AstValidator;
import io.github.serfom256.intentflow.core.pipeline.AuditLog;
import io.github.serfom256.intentflow.core.pipeline.DslCache;
import io.github.serfom256.intentflow.core.pipeline.DslParser;
import io.github.serfom256.intentflow.core.pipeline.IntentPipeline;
import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import io.github.serfom256.intentflow.core.pipeline.LlmRateLimiter;
import io.github.serfom256.intentflow.core.pipeline.PipelineListener;
import io.github.serfom256.intentflow.core.pipeline.PipelineTracer;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import io.github.serfom256.intentflow.core.pipeline.SqlBuilder;
import io.github.serfom256.intentflow.core.pipeline.TextNormalizer;
import io.github.serfom256.intentflow.core.pipeline.UriBuilder;
import io.github.serfom256.intentflow.core.pipeline.impl.DefaultIntentPipeline;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public final class IntentPipelineBuilder {

    private @Nullable LlmClient llm;
    private @Nullable SqlBuilder sqlBuilder;
    private @Nullable QueryExecutor executor;
    private @Nullable ServiceCatalog catalog;
    private TextNormalizer normalizer = TextNormalizers.create();
    private @Nullable DslCache cache;
    private DslParser parser = DslParsers.create();
    private AstValidator validator = AstValidators.create();
    private UriBuilder uriBuilder = UriBuilders.create();
    private int maxRepairs = 1;
    private AccessPolicy accessPolicy = AccessPolicy.ALLOW_ALL;
    private Supplier<IntentContext> defaultCaller = IntentContexts::anonymous;
    private PipelineListener listener = PipelineListener.NONE;
    private PipelineTracer tracer = PipelineTracer.NONE;
    private LlmRateLimiter rateLimiter = LlmRateLimiter.NONE;
    private AuditLog audit = AuditLog.NONE;
    private boolean logValues;

    IntentPipelineBuilder() {
    }

    public IntentPipelineBuilder llm(LlmClient llm) {
        this.llm = llm;
        return this;
    }

    public IntentPipelineBuilder sqlBuilder(SqlBuilder sqlBuilder) {
        this.sqlBuilder = sqlBuilder;
        return this;
    }

    public IntentPipelineBuilder executor(QueryExecutor executor) {
        this.executor = executor;
        return this;
    }

    public IntentPipelineBuilder catalog(ServiceCatalog catalog) {
        this.catalog = catalog;
        return this;
    }

    public IntentPipelineBuilder normalizer(TextNormalizer normalizer) {
        this.normalizer = normalizer;
        return this;
    }

    public IntentPipelineBuilder cache(DslCache cache) {
        this.cache = cache;
        return this;
    }

    public IntentPipelineBuilder parser(DslParser parser) {
        this.parser = parser;
        return this;
    }

    public IntentPipelineBuilder validator(AstValidator validator) {
        this.validator = validator;
        return this;
    }

    public IntentPipelineBuilder uriBuilder(UriBuilder uriBuilder) {
        this.uriBuilder = uriBuilder;
        return this;
    }

    public IntentPipelineBuilder maxRepairs(int maxRepairs) {
        if (maxRepairs < 0) {
            throw new IllegalArgumentException("maxRepairs must not be negative: " + maxRepairs);
        }
        this.maxRepairs = maxRepairs;
        return this;
    }

    public IntentPipelineBuilder accessPolicy(AccessPolicy accessPolicy) {
        this.accessPolicy = accessPolicy;
        return this;
    }

    public IntentPipelineBuilder defaultCaller(Supplier<IntentContext> defaultCaller) {
        this.defaultCaller = defaultCaller;
        return this;
    }

    public IntentPipelineBuilder listener(PipelineListener listener) {
        this.listener = listener;
        return this;
    }

    public IntentPipelineBuilder tracer(PipelineTracer tracer) {
        this.tracer = tracer;
        return this;
    }

    public IntentPipelineBuilder rateLimiter(LlmRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
        return this;
    }

    public IntentPipelineBuilder audit(AuditLog audit) {
        this.audit = audit;
        return this;
    }

    public IntentPipelineBuilder logValues(boolean logValues) {
        this.logValues = logValues;
        return this;
    }

    public IntentPipeline build() {
        return new DefaultIntentPipeline(normalizer, cache != null ? cache : DslCaches.create(),
                require(llm, "llm"), parser, validator, require(sqlBuilder, "sqlBuilder"),
                require(executor, "executor"), uriBuilder, require(catalog, "catalog"), maxRepairs, accessPolicy,
                defaultCaller, listener, tracer, rateLimiter, audit, logValues);
    }

    private static <T> T require(@Nullable T value, String name) {
        if (value == null) {
            throw new IllegalStateException("IntentPipeline requires " + name + "(...) before build()");
        }
        return value;
    }
}
