package io.github.serfom256.intentflow.springdata.jdbc;

import io.github.serfom256.intentflow.core.testing.TestDatabase;
import io.github.serfom256.intentflow.dialect.mysql.MySqlDatabase;

class SpringDataJdbcMySqlDifferentialIT extends SpringDataJdbcDifferentialIT {

    @Override
    protected TestDatabase database() {
        return MySqlDatabase.MYSQL;
    }
}
