package io.github.serfom256.intentflow.dialect.oracle;

import io.github.serfom256.intentflow.core.testing.DialectConformanceTest;
import io.github.serfom256.intentflow.core.testing.TestDatabase;

class OracleConformanceIT extends DialectConformanceTest {

    @Override
    protected TestDatabase database() {
        return OracleDatabase.FREE;
    }
}
