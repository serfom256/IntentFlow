package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.pipeline.SqlBuilder;
import io.github.serfom256.intentflow.core.pipeline.SqlDialect;
import io.github.serfom256.intentflow.core.pipeline.ValidatedQuery;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;

import java.util.function.Supplier;

final class LazySqlBuilder implements SqlBuilder {

    private final Supplier<SqlDialect> dialect;
    private volatile SqlBuilder delegate;

    LazySqlBuilder(Supplier<SqlDialect> dialect) {
        this.dialect = dialect;
    }

    @Override
    public BoundQuery build(ValidatedQuery query) {
        SqlBuilder builder = delegate;
        if (builder == null) {
            synchronized (this) {
                if (delegate == null) {
                    delegate = SqlBuilders.create(dialect.get());
                }
                builder = delegate;
            }
        }
        return builder.build(query);
    }
}
