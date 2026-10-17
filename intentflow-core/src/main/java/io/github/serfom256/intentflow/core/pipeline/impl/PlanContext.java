package io.github.serfom256.intentflow.core.pipeline.impl;

import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.pipeline.ResultShape;
import io.github.serfom256.intentflow.core.pipeline.RuntimeRestriction;
import io.github.serfom256.intentflow.core.pipeline.SqlCapabilities;
import io.github.serfom256.intentflow.core.pipeline.SqlDialect;
import io.github.serfom256.intentflow.core.schema.SchemaFacts;
import io.github.serfom256.intentflow.core.schema.TableFacts;
import io.github.serfom256.intentflow.core.sql.Optimization;
import org.jspecify.annotations.Nullable;

import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

final class PlanContext {

    private final SchemaFacts facts;
    private final Set<Optimization> enabled;
    private final ResultShape shape;
    private final SqlDialect dialect;
    private final ZoneId zone;
    private final Function<Class<?>, List<RuntimeRestriction>> runtime;
    private final Set<Optimization> applied = EnumSet.noneOf(Optimization.class);

    PlanContext(SchemaFacts facts, Set<Optimization> enabled, ResultShape shape, SqlDialect dialect, ZoneId zone,
            Function<Class<?>, List<RuntimeRestriction>> runtime) {
        this.runtime = runtime;
        this.facts = facts;
        this.dialect = dialect;
        this.zone = zone;
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

    List<RuntimeRestriction> runtimeRestrictions(ServiceDescriptor service) {
        return runtime.apply(service.resultType());
    }

    ResultShape shape() {
        return shape;
    }

    SqlCapabilities capabilities() {
        return dialect.capabilities();
    }

    ZoneId zone() {
        return zone;
    }

    String dialectName() {
        return dialect.name();
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
