package io.github.serfom256.intentflow.core.pipeline.impl;

import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.pipeline.ResultShape;
import io.github.serfom256.intentflow.core.schema.SchemaFacts;
import io.github.serfom256.intentflow.core.schema.TableFacts;
import io.github.serfom256.intentflow.core.sql.Optimization;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

final class PlanContext {

    private final SchemaFacts facts;
    private final Set<Optimization> enabled;
    private final ResultShape shape;
    private final Set<Optimization> applied = EnumSet.noneOf(Optimization.class);

    PlanContext(SchemaFacts facts, Set<Optimization> enabled, ResultShape shape) {
        this.facts = facts;
        this.enabled = enabled.isEmpty() ? EnumSet.noneOf(Optimization.class) : EnumSet.copyOf(enabled);
        this.shape = shape;
    }

    TableFacts facts(String table) {
        return facts.table(table);
    }

    @Nullable
    String restriction(ServiceName service) {
        return facts.restriction(service);
    }

    ResultShape shape() {
        return shape;
    }

    boolean enabled(Optimization optimization) {
        return enabled.contains(optimization);
    }

    void applied(Optimization optimization) {
        applied.add(optimization);
    }

    List<String> applied() {
        return applied.stream().map(Optimization::id).toList();
    }
}
