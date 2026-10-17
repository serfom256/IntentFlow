package io.github.serfom256.intentflow.jpa;

import io.github.serfom256.intentflow.core.catalog.NameResolver;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import io.github.serfom256.intentflow.core.testing.ExecutorDifferentialTest;
import io.github.serfom256.intentflow.core.testing.GadgetKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.junit.jupiter.api.AfterAll;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

class JpaDifferentialIT extends ExecutorDifferentialTest {

    @Entity
    @Table(name = "gadget")
    @IntentService(value = "gadgets", uri = "/gadgets/{{id}}", maxTake = 500, defaultSort = "id")
    public static class Gadget {
        @Id
        @IntentField
        Long id;
        @IntentField
        String name;
        @Enumerated(EnumType.STRING)
        @IntentField(caseSensitive = true)
        GadgetKind kind;
        @IntentField
        BigDecimal price;
        @IntentField
        Integer stock;
        @IntentField
        Boolean active;
        @Column(name = "created_at")
        @IntentField
        LocalDateTime createdAt;
    }

    private EntityManagerFactory emf;

    @Override
    protected Class<?> entityType() {
        return Gadget.class;
    }

    @Override
    protected List<NameResolver> nameResolvers() {
        return List.of(new JpaNameResolver());
    }

    @Override
    protected QueryExecutor createExecutor(String jdbcUrl, String user, String password) {
        emf = TestEntityManagers.create(jdbcUrl, user, password, Gadget.class);
        return new JpaQueryExecutor(emf.createEntityManager());
    }

    @AfterAll
    void close() {
        if (emf != null) {
            emf.close();
        }
    }
}
