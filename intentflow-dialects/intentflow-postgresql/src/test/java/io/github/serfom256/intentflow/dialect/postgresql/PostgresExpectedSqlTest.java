package io.github.serfom256.intentflow.dialect.postgresql;

import io.github.serfom256.intentflow.core.pipeline.SqlDialect;
import io.github.serfom256.intentflow.core.testing.ExpectedSqlTest;

class PostgresExpectedSqlTest extends ExpectedSqlTest {

    @Override
    protected SqlDialect dialect() {
        return new PostgresDialect();
    }
}
