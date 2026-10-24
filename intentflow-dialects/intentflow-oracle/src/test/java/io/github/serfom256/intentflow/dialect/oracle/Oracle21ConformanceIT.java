package io.github.serfom256.intentflow.dialect.oracle;

import io.github.serfom256.intentflow.core.testing.DialectConformanceTest;
import io.github.serfom256.intentflow.core.testing.TestDatabase;

class Oracle21ConformanceIT extends DialectConformanceTest {

    @Override
    protected TestDatabase database() {
        return OracleDatabase.XE_21;
    }
}
