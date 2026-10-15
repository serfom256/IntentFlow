package io.github.serfom256.intentflow.jimmer;

import io.github.serfom256.intentflow.core.catalog.NameResolver;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import io.github.serfom256.intentflow.core.testing.DifferentialDatabase;
import io.github.serfom256.intentflow.core.testing.ExecutorDifferentialTest;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.dialect.MySqlDialect;
import org.babyfish.jimmer.sql.dialect.PostgresDialect;
import org.babyfish.jimmer.sql.runtime.ConnectionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

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
    protected QueryExecutor createExecutor(String jdbcUrl, String user, String password) {
        JSqlClient sqlClient = JSqlClient.newBuilder()
                .setConnectionManager(ConnectionManager.simpleConnectionManager(
                        new DriverManagerDataSource(jdbcUrl, user, password)))
                .setDialect(database() == DifferentialDatabase.MYSQL ? new MySqlDialect() : new PostgresDialect())
                .build();
        return new JimmerQueryExecutor(sqlClient);
    }
}
