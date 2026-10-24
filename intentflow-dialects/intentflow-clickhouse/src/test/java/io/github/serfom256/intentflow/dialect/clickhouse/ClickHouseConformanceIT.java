package io.github.serfom256.intentflow.dialect.clickhouse;

import io.github.serfom256.intentflow.core.testing.DialectConformanceTest;
import io.github.serfom256.intentflow.core.testing.TestDatabase;

class ClickHouseConformanceIT extends DialectConformanceTest {

    @Override
    protected TestDatabase database() {
        return ClickHouseDatabase.SERVER;
    }
}
