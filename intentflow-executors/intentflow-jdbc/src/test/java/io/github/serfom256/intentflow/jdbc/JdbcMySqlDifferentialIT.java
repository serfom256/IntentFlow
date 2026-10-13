package io.github.serfom256.intentflow.jdbc;

import io.github.serfom256.intentflow.core.testing.DifferentialDatabase;

class JdbcMySqlDifferentialIT extends JdbcDifferentialIT {

    @Override
    protected DifferentialDatabase database() {
        return DifferentialDatabase.MYSQL;
    }
}
