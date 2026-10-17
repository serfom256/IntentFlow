package io.github.serfom256.intentflow.dialect.mysql;

import io.github.serfom256.intentflow.core.testing.DialectConformanceTest;
import io.github.serfom256.intentflow.core.testing.DifferentialDatabase;
import io.github.serfom256.intentflow.core.testing.TestDatabase;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class MySqlConformanceIT extends DialectConformanceTest {

    @Override
    protected TestDatabase database() {
        return DifferentialDatabase.MYSQL;
    }
}
