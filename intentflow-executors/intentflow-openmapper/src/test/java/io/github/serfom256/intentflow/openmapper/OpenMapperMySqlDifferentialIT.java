package io.github.serfom256.intentflow.openmapper;

import io.github.serfom256.intentflow.core.testing.TestDatabase;
import io.github.serfom256.intentflow.dialect.mysql.MySqlDatabase;

class OpenMapperMySqlDifferentialIT extends OpenMapperDifferentialIT {

    @Override
    protected TestDatabase database() {
        return MySqlDatabase.MYSQL;
    }
}
