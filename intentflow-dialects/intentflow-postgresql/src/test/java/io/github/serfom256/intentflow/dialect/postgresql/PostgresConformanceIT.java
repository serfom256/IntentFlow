package io.github.serfom256.intentflow.dialect.postgresql;

import io.github.serfom256.intentflow.core.testing.DialectConformanceTest;
import io.github.serfom256.intentflow.core.testing.TestDatabase;
import io.github.serfom256.intentflow.dialect.postgresql.PostgresDatabase;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class PostgresConformanceIT extends DialectConformanceTest {

    @Override
    protected TestDatabase database() {
        return PostgresDatabase.POSTGRES;
    }
}
