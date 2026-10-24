package io.github.serfom256.intentflow.jimmer;

import io.github.serfom256.intentflow.core.testing.TestDatabase;
import io.github.serfom256.intentflow.dialect.mysql.MySqlDatabase;

class JimmerMySqlDifferentialIT extends JimmerDifferentialIT {

    @Override
    protected TestDatabase database() {
        return MySqlDatabase.MYSQL;
    }
}
