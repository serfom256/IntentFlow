package io.github.serfom256.intentflow.dialect.h2;

import io.github.serfom256.intentflow.core.pipeline.SqlDialect;
import io.github.serfom256.intentflow.core.testing.ExpectedSqlTest;

class H2ExpectedSqlTest extends ExpectedSqlTest {

    @Override
    protected SqlDialect dialect() {
        return new H2Dialect();
    }
}
