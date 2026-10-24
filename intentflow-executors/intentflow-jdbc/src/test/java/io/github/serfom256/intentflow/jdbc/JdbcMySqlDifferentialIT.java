package io.github.serfom256.intentflow.jdbc;

import io.github.serfom256.intentflow.core.testing.TestDatabase;
import io.github.serfom256.intentflow.dialect.mysql.MySqlDatabase;

class JdbcMySqlDifferentialIT extends JdbcDifferentialIT {

    @Override
    protected TestDatabase database() {
        return MySqlDatabase.MYSQL;
    }
}
