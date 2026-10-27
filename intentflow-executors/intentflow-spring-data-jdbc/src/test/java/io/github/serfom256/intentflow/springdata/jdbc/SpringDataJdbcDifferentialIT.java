package io.github.serfom256.intentflow.springdata.jdbc;

import io.github.serfom256.intentflow.core.catalog.NameResolver;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import io.github.serfom256.intentflow.core.testing.ExecutorDifferentialTest;
import io.github.serfom256.intentflow.core.testing.GadgetKind;
import io.github.serfom256.intentflow.core.testing.TestDatabase;
import io.github.serfom256.intentflow.dialect.postgresql.PostgresDatabase;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

class SpringDataJdbcDifferentialIT extends ExecutorDifferentialTest {

    @Table("gadget")
    @IntentService(value = "gadgets", uri = "/gadgets/{{id}}", maxTake = 500, defaultSort = "id")
    record Gadget(@Id @IntentField Long id, @IntentField String name,
            @IntentField(caseSensitive = true) GadgetKind kind, @IntentField BigDecimal price,
            @IntentField Integer stock, @IntentField Boolean active,
            @Column("created_at") @IntentField LocalDateTime createdAt) {
    }

    @Override
    protected Class<?> entityType() {
        return Gadget.class;
    }

    @Override
    protected List<NameResolver> nameResolvers() {
        return List.of(new SpringDataJdbcNameResolver());
    }

    @Override
    protected TestDatabase database() {
        return PostgresDatabase.POSTGRES;
    }

    @Override
    protected QueryExecutor createExecutor(String jdbcUrl, String user, String password) {
        return SpringDataJdbcQueryExecutor.of(new SingleConnectionDataSource(jdbcUrl, user, password, true));
    }
}
