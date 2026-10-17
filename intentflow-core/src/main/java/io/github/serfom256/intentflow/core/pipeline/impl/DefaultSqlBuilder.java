package io.github.serfom256.intentflow.core.pipeline.impl;

import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.pipeline.ResultShape;
import io.github.serfom256.intentflow.core.pipeline.SqlBuilder;
import io.github.serfom256.intentflow.core.pipeline.SqlDialect;
import io.github.serfom256.intentflow.core.pipeline.ValidatedQuery;
import io.github.serfom256.intentflow.core.schema.SchemaFacts;
import io.github.serfom256.intentflow.core.sql.Optimization;
import io.github.serfom256.intentflow.core.sql.plan.Select;
import io.github.serfom256.intentflow.core.sql.render.SqlRenderer;

import java.util.EnumSet;
import java.util.Set;

public final class DefaultSqlBuilder implements SqlBuilder {

    private final SqlRenderer renderer;
    private final SchemaFacts facts;
    private final Set<Optimization> optimizations;

    public DefaultSqlBuilder(SqlDialect dialect) {
        this(dialect, SchemaFacts.none(), EnumSet.allOf(Optimization.class));
    }

    public DefaultSqlBuilder(SqlDialect dialect, SchemaFacts facts, Set<Optimization> optimizations) {
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
        PlanContext context = new PlanContext(facts, optimizations, shape);
        Select select = QueryPlanner.plan(query, context);
        return renderer.render(select, context.applied());
    }
}
