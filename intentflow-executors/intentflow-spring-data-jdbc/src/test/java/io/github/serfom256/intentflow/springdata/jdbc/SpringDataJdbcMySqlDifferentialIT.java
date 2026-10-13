package io.github.serfom256.intentflow.springdata.jdbc;

import io.github.serfom256.intentflow.core.testing.DifferentialDatabase;

class SpringDataJdbcMySqlDifferentialIT extends SpringDataJdbcDifferentialIT {

    @Override
    protected DifferentialDatabase database() {
        return DifferentialDatabase.MYSQL;
    }
}
