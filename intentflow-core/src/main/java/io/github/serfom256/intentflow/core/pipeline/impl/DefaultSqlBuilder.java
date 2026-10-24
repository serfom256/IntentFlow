package io.github.serfom256.intentflow.core.pipeline.impl;

import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.pipeline.ResultShape;
import io.github.serfom256.intentflow.core.pipeline.RuntimeRestriction;
import io.github.serfom256.intentflow.core.pipeline.SqlBuilder;
import io.github.serfom256.intentflow.core.pipeline.SqlDialect;
import io.github.serfom256.intentflow.core.pipeline.ValidatedQuery;
import io.github.serfom256.intentflow.core.schema.SchemaFacts;
import io.github.serfom256.intentflow.core.sql.Optimization;
import io.github.serfom256.intentflow.core.sql.plan.Select;
import io.github.serfom256.intentflow.core.sql.render.SqlRenderer;
import org.jspecify.annotations.Nullable;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public final class DefaultSqlBuilder implements SqlBuilder {

    private final SqlDialect dialect;
    private final SqlRenderer renderer;
    private final SchemaFacts facts;
    private final Set<Optimization> optimizations;
    private static final int RELATED_CHUNK = 512;

    private final ZoneId zone;

    public DefaultSqlBuilder(SqlDialect dialect) {
        this(dialect, SchemaFacts.none(), EnumSet.allOf(Optimization.class));
    }

    public DefaultSqlBuilder(SqlDialect dialect, SchemaFacts facts, Set<Optimization> optimizations) {
        this(dialect, facts, optimizations, ZoneId.systemDefault());
    }

    public DefaultSqlBuilder(SqlDialect dialect, SchemaFacts facts, Set<Optimization> optimizations, ZoneId zone) {
        this.zone = zone;
        this.dialect = dialect;
        this.renderer = new SqlRenderer(dialect);
        this.facts = facts;
        this.optimizations = optimizations.isEmpty() ? EnumSet.noneOf(Optimization.class)
                : EnumSet.copyOf(optimizations);
    }

    @Override
    public BoundQuery build(ValidatedQuery query) {
        return build(query, ResultShape.ROWS);
    }

    @Override
    public BoundQuery build(ValidatedQuery query, ResultShape shape) {
        return build(query, shape, type -> List.of());
    }

    @Override
    public BoundQuery build(ValidatedQuery query, ResultShape shape,
            Function<Class<?>, List<RuntimeRestriction>> restrictions) {
        PlanContext context = new PlanContext(facts, optimizations, shape, dialect, zone, restrictions);
        Select select = QueryPlanner.plan(query, context);
        return renderer.render(select, context.applied());
    }

    @Override
    public @Nullable Object read(@Nullable Object value) {
        return dialect.read(value);
    }

    @Override
    public boolean supportsKeyset(ValidatedQuery query) {
        return QueryPlanner.keysetEligible(query, new PlanContext(facts, optimizations, ResultShape.ROWS, dialect, zone,
                type -> List.of()));
    }

    @Override
    public BoundQuery buildAfter(ValidatedQuery query, ResultShape shape,
            Function<Class<?>, List<RuntimeRestriction>> restrictions, List<Object> after) {
        PlanContext context = new PlanContext(facts, optimizations, shape, dialect, zone, restrictions);
        if (!QueryPlanner.keysetEligible(query, context)) {
            throw new IllegalArgumentException("keyset paging needs a sort on non-null columns that ends in a key");
        }
        return renderer.render(QueryPlanner.plan(query, context, after), context.applied());
    }

    @Override
    public List<BoundQuery> buildRelated(ServiceDescriptor target, String column, List<?> keys,
            Function<Class<?>, List<RuntimeRestriction>> restrictions) {
        int chunk = Math.min(RELATED_CHUNK, dialect.capabilities().maxInListSize());
        List<BoundQuery> queries = new ArrayList<>();
        for (int from = 0; from < keys.size(); from += chunk) {
            List<Object> part = new ArrayList<>(keys.subList(from, Math.min(keys.size(), from + chunk)));
            int padded = Math.min(chunk, Integer.highestOneBit(part.size() - 1) << 1);
            while (part.size() < padded) {
                part.add(part.getLast());
            }
            PlanContext context = new PlanContext(facts, optimizations, ResultShape.ROWS, dialect, zone, restrictions);
            queries.add(renderer.render(QueryPlanner.related(target, column, part, context), context.applied()));
        }
        return queries;
    }
}
