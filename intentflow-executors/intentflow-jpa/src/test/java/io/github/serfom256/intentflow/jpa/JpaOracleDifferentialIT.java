package io.github.serfom256.intentflow.jpa;

import io.github.serfom256.intentflow.core.testing.TestDatabase;
import io.github.serfom256.intentflow.dialect.oracle.OracleDatabase;

class JpaOracleDifferentialIT extends JpaDifferentialIT {

    @Override
    protected TestDatabase database() {
        return OracleDatabase.FREE;
    }
}
