package io.github.serfom256.intentflow.openmapper;

import io.github.serfom256.intentflow.core.testing.TestDatabase;
import io.github.serfom256.intentflow.dialect.oracle.OracleDatabase;

class OpenMapperOracleDifferentialIT extends OpenMapperDifferentialIT {

    @Override
    protected TestDatabase database() {
        return OracleDatabase.FREE;
    }
}
