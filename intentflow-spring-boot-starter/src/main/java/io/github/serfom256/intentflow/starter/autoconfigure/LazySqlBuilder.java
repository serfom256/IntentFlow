package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.pipeline.ResultShape;
import io.github.serfom256.intentflow.core.pipeline.RuntimeRestriction;
import io.github.serfom256.intentflow.core.pipeline.SqlBuilder;
import io.github.serfom256.intentflow.core.pipeline.SqlDialect;
import io.github.serfom256.intentflow.core.pipeline.ValidatedQuery;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

final class LazySqlBuilder implements SqlBuilder {

    private final Supplier<SqlDialect> dialect;
    private final Function<SqlDialect, SqlBuilder> factory;
    private volatile SqlBuilder delegate;

    LazySqlBuilder(Supplier<SqlDialect> dialect, Function<SqlDialect, SqlBuilder> factory) {
        this.dialect = dialect;
        this.factory = factory;
    }

    @Override
    public BoundQuery build(ValidatedQuery query) {
        return delegate().build(query);
    }

    @Override
    public BoundQuery build(ValidatedQuery query, ResultShape shape) {
        return delegate().build(query, shape);
    }

    @Override
    public BoundQuery build(ValidatedQuery query, ResultShape shape,
            Function<Class<?>, List<RuntimeRestriction>> restrictions) {
        return delegate().build(query, shape, restrictions);
    }

    @Override
    public List<BoundQuery> buildRelated(ServiceDescriptor target, String column, List<?> keys,
            Function<Class<?>, List<RuntimeRestriction>> restrictions) {
        return delegate().buildRelated(target, column, keys, restrictions);
    }

    private SqlBuilder delegate() {
        SqlBuilder builder = delegate;
        if (builder == null) {
            synchronized (this) {
                if (delegate == null) {
                    delegate = factory.apply(dialect.get());
                }
                builder = delegate;
            }
        }
        return builder;
    }
}
