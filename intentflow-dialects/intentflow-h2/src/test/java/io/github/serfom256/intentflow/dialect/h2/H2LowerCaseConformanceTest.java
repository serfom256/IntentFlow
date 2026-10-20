package io.github.serfom256.intentflow.dialect.h2;

import io.github.serfom256.intentflow.core.testing.DialectConformanceTest;
import io.github.serfom256.intentflow.core.testing.TestDatabase;

class H2LowerCaseConformanceTest extends DialectConformanceTest {

    @Override
    protected TestDatabase database() {
        return H2Database.LOWER_CASE;
    }
}
