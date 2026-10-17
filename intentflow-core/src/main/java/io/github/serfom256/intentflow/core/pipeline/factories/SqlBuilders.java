package io.github.serfom256.intentflow.core.pipeline.factories;

import io.github.serfom256.intentflow.core.pipeline.SqlBuilder;
import io.github.serfom256.intentflow.core.pipeline.SqlDialect;
import io.github.serfom256.intentflow.core.pipeline.impl.DefaultSqlBuilder;
import io.github.serfom256.intentflow.core.schema.SchemaFacts;
import io.github.serfom256.intentflow.core.sql.Optimization;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.EnumSet;
import java.util.Set;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SqlBuilders {

    public static SqlBuilder create(SqlDialect dialect) {
        return new DefaultSqlBuilder(dialect);
    }

    public static SqlBuilder create(SqlDialect dialect, SchemaFacts facts) {
        return new DefaultSqlBuilder(dialect, facts, EnumSet.allOf(Optimization.class));
    }

    public static SqlBuilder create(SqlDialect dialect, SchemaFacts facts, Set<Optimization> optimizations) {
        return new DefaultSqlBuilder(dialect, facts, optimizations);
    }
}
