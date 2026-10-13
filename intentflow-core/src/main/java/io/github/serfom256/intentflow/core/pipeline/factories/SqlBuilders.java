package io.github.serfom256.intentflow.core.pipeline.factories;

import io.github.serfom256.intentflow.core.pipeline.SqlBuilder;
import io.github.serfom256.intentflow.core.pipeline.SqlDialect;
import io.github.serfom256.intentflow.core.pipeline.impl.DefaultSqlBuilder;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SqlBuilders {

    public static SqlBuilder create(SqlDialect dialect) {
        return new DefaultSqlBuilder(dialect);
    }
}
