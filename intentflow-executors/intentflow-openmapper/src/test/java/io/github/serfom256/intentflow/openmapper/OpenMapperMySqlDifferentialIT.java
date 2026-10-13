package io.github.serfom256.intentflow.openmapper;

import io.github.serfom256.intentflow.core.testing.DifferentialDatabase;

class OpenMapperMySqlDifferentialIT extends OpenMapperDifferentialIT {

    @Override
    protected DifferentialDatabase database() {
        return DifferentialDatabase.MYSQL;
    }
}
