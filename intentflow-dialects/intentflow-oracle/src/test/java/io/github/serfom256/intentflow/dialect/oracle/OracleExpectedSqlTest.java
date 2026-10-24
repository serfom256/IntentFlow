package io.github.serfom256.intentflow.dialect.oracle;

import io.github.serfom256.intentflow.core.pipeline.SqlDialect;
import io.github.serfom256.intentflow.core.testing.ExpectedSqlTest;

class OracleExpectedSqlTest extends ExpectedSqlTest {

    @Override
    protected SqlDialect dialect() {
        return new OracleDialect();
    }
}
