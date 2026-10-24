package io.github.serfom256.intentflow.dialect.clickhouse;

import io.github.serfom256.intentflow.core.pipeline.SqlDialect;
import io.github.serfom256.intentflow.core.testing.ExpectedSqlTest;

class ClickHouseExpectedSqlTest extends ExpectedSqlTest {

    @Override
    protected SqlDialect dialect() {
        return new ClickHouseDialect();
    }
}
