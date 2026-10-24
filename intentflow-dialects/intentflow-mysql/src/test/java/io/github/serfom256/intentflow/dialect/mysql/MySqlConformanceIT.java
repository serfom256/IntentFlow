package io.github.serfom256.intentflow.dialect.mysql;

import io.github.serfom256.intentflow.core.testing.DialectConformanceTest;
import io.github.serfom256.intentflow.core.testing.TestDatabase;
import io.github.serfom256.intentflow.dialect.mysql.MySqlDatabase;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class MySqlConformanceIT extends DialectConformanceTest {

    @Override
    protected TestDatabase database() {
        return MySqlDatabase.MYSQL;
    }
}
