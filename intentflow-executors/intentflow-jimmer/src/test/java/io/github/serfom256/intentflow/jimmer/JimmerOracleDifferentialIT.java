package io.github.serfom256.intentflow.jimmer;

import io.github.serfom256.intentflow.core.testing.TestDatabase;
import io.github.serfom256.intentflow.dialect.oracle.OracleDatabase;

class JimmerOracleDifferentialIT extends JimmerDifferentialIT {

    @Override
    protected TestDatabase database() {
        return OracleDatabase.FREE;
    }
}
