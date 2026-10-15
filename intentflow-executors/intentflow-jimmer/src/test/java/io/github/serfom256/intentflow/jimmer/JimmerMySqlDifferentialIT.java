package io.github.serfom256.intentflow.jimmer;

import io.github.serfom256.intentflow.core.testing.DifferentialDatabase;

class JimmerMySqlDifferentialIT extends JimmerDifferentialIT {

    @Override
    protected DifferentialDatabase database() {
        return DifferentialDatabase.MYSQL;
    }
}
