package io.github.serfom256.intentflow.jpa;

import io.github.serfom256.intentflow.core.testing.DifferentialDatabase;

class JpaMySqlDifferentialIT extends JpaDifferentialIT {

    @Override
    protected DifferentialDatabase database() {
        return DifferentialDatabase.MYSQL;
    }
}
