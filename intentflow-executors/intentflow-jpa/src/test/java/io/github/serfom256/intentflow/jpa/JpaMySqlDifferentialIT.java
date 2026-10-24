package io.github.serfom256.intentflow.jpa;

import io.github.serfom256.intentflow.core.testing.TestDatabase;
import io.github.serfom256.intentflow.dialect.mysql.MySqlDatabase;

class JpaMySqlDifferentialIT extends JpaDifferentialIT {

    @Override
    protected TestDatabase database() {
        return MySqlDatabase.MYSQL;
    }
}
