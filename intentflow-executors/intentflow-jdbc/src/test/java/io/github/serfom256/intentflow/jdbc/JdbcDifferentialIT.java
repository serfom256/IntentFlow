package io.github.serfom256.intentflow.jdbc;

import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import io.github.serfom256.intentflow.core.testing.ExecutorDifferentialTest;
import io.github.serfom256.intentflow.core.testing.GadgetKind;
import io.github.serfom256.intentflow.core.testing.TestDatabase;
import io.github.serfom256.intentflow.dialect.postgresql.PostgresDatabase;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;

class JdbcDifferentialIT extends ExecutorDifferentialTest {

    @IntentService(value = "gadgets", table = "gadget", uri = "/gadgets/{{id}}", maxTake = 500, defaultSort = "id")
    record Gadget(
            @IntentField long id,
            @IntentField String name,
            @IntentField(caseSensitive = true) GadgetKind kind,
            @IntentField BigDecimal price,
            @IntentField Integer stock,
            @IntentField Boolean active,
            @IntentField LocalDateTime createdAt) {
    }

    @Override
    protected Class<?> entityType() {
        return Gadget.class;
    }

    @Override
    protected TestDatabase database() {
        return PostgresDatabase.POSTGRES;
    }

    @Override
    protected QueryExecutor createExecutor(String jdbcUrl, String user, String password) {
        return JdbcQueryExecutor.of(new SingleConnectionDataSource(jdbcUrl, user, password, true));
    }
}
