package io.github.serfom256.intentflow.dialect.mysql;

import io.github.serfom256.intentflow.core.pipeline.SqlDialect;
import io.github.serfom256.intentflow.core.testing.ExpectedSqlTest;

class MySqlExpectedSqlTest extends ExpectedSqlTest {

    @Override
    protected SqlDialect dialect() {
        return new MySqlDialect();
    }
}
