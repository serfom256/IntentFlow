package io.github.serfom256.intentflow.core.pipeline.impl;

import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.pipeline.ResultShape;
import io.github.serfom256.intentflow.core.pipeline.RuntimeRestriction;
import io.github.serfom256.intentflow.core.pipeline.SqlBuilder;
import io.github.serfom256.intentflow.core.pipeline.SqlDialect;
import io.github.serfom256.intentflow.core.pipeline.ValidatedQuery;
import io.github.serfom256.intentflow.core.schema.SchemaFacts;
import io.github.serfom256.intentflow.core.sql.Optimization;
import io.github.serfom256.intentflow.core.sql.plan.Select;
import io.github.serfom256.intentflow.core.sql.render.SqlRenderer;

import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public final class DefaultSqlBuilder implements SqlBuilder {

    private final SqlDialect dialect;
    private final SqlRenderer renderer;
    private final SchemaFacts facts;
    private final Set<Optimization> optimizations;
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
}
