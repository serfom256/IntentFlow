package io.github.serfom256.intentflow.jimmer;

import io.github.serfom256.intentflow.core.catalog.NameResolver;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import io.github.serfom256.intentflow.core.testing.ExecutorDifferentialTest;
import io.github.serfom256.intentflow.core.testing.TestDatabase;
import io.github.serfom256.intentflow.dialect.mysql.MySqlDatabase;
import io.github.serfom256.intentflow.dialect.oracle.OracleDatabase;
import io.github.serfom256.intentflow.dialect.postgresql.PostgresDatabase;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.dialect.Dialect;
import org.babyfish.jimmer.sql.dialect.MySqlDialect;
import org.babyfish.jimmer.sql.dialect.OracleDialect;
import org.babyfish.jimmer.sql.dialect.PostgresDialect;
import org.babyfish.jimmer.sql.runtime.ConnectionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.util.List;

class JimmerDifferentialIT extends ExecutorDifferentialTest {

    @Override
    protected Class<?> entityType() {
        return JimmerGadget.class;
    }

    @Override
    protected List<NameResolver> nameResolvers() {
        return List.of(new JimmerNameResolver());
    }

    @Override
    protected TestDatabase database() {
        return PostgresDatabase.POSTGRES;
    }

    @Override
    protected QueryExecutor createExecutor(String jdbcUrl, String user, String password) {
        JSqlClient sqlClient = JSqlClient.newBuilder()
                .setConnectionManager(ConnectionManager.simpleConnectionManager(
                        new SingleConnectionDataSource(jdbcUrl, user, password, true)))
                .setDialect(dialect(database()))
                .build();
        return new JimmerQueryExecutor(sqlClient);
    }

    private static Dialect dialect(TestDatabase database) {
        if (database == MySqlDatabase.MYSQL) {
            return new MySqlDialect();
        }
        return database == OracleDatabase.FREE ? new OracleDialect() : new PostgresDialect();
    }
}
