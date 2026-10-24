package io.github.serfom256.intentflow.springdata.jdbc;

import io.github.serfom256.intentflow.core.testing.TestDatabase;
import io.github.serfom256.intentflow.dialect.oracle.OracleDatabase;

class SpringDataJdbcOracleDifferentialIT extends SpringDataJdbcDifferentialIT {

    @Override
    protected TestDatabase database() {
        return OracleDatabase.FREE;
    }
}
