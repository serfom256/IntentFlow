package io.github.serfom256.intentflow.jdbc;

import io.github.serfom256.intentflow.core.testing.TestDatabase;
import io.github.serfom256.intentflow.dialect.oracle.OracleDatabase;

class JdbcOracleDifferentialIT extends JdbcDifferentialIT {

    @Override
    protected TestDatabase database() {
        return OracleDatabase.FREE;
    }
}
