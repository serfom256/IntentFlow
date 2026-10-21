package io.github.serfom256.intentflow.jpa;

import io.github.serfom256.intentflow.core.api.AggregateRow;
import io.github.serfom256.intentflow.core.api.IntentResponse;
import io.github.serfom256.intentflow.core.api.factories.IntentRequests;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceDescriptors;
import io.github.serfom256.intentflow.core.pipeline.IntentPipeline;
import io.github.serfom256.intentflow.core.pipeline.factories.IntentPipelines;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.core.schema.SchemaFacts;
import io.github.serfom256.intentflow.core.testing.DifferentialDatabase;
import io.github.serfom256.intentflow.core.testing.TestDatabase;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@Testcontainers(disabledWithoutDocker = true)
class JpaOrdinalEnumIT {

    enum Priority {
        LOW, MEDIUM, HIGH
    }

    @Entity
    @Table(name = "ord_ticket")
    @IntentService(value = "tickets", uri = "/tickets/{{id}}", defaultSort = "id")
    public static class Ticket {
        @Id
        @IntentField
        Long id;
        @IntentField
        Priority priority;
    }

    private static final Map<DifferentialDatabase, EntityManager> MANAGERS = new EnumMap<>(DifferentialDatabase.class);

    private static final ServiceCatalog CATALOG = ServiceCatalogs.of(
            ServiceDescriptors.fromAnnotated(Ticket.class, List.of(new JpaNameResolver())));

    private static final SchemaFacts FACTS = SchemaFacts.of(
            Map.of("ord_ticket", ServiceDescriptors.factsOf(Ticket.class, List.of(new JpaNameResolver()))), Map.of());

    private static synchronized EntityManager manager(DifferentialDatabase db) {
        return MANAGERS.computeIfAbsent(db, d -> {
            TestDatabase.Endpoint e = d.endpoint();
            try (Connection c = DriverManager.getConnection(e.url(), e.user(), e.password());
                    Statement s = c.createStatement()) {
                s.execute("DROP TABLE IF EXISTS ord_ticket");
                s.execute("CREATE TABLE ord_ticket (id BIGINT PRIMARY KEY, priority SMALLINT)");
                s.execute("INSERT INTO ord_ticket VALUES (1, 0), (2, 2), (3, 1), (4, NULL), (5, 2)");
            } catch (SQLException ex) {
                throw new IllegalStateException(ex);
            }
            return TestEntityManagers.create(e.url(), e.user(), e.password(), Ticket.class).createEntityManager();
        });
    }

    private static IntentResponse<Ticket> resolve(DifferentialDatabase db, String dsl) {
        manager(db).clear();
        IntentPipeline pipeline = IntentPipelines.builder()
                .llm((t, c) -> DslSources.of(dsl))
                .sqlBuilder(SqlBuilders.create(db.dialect(), FACTS))
                .executor(new JpaQueryExecutor(manager(db)))
                .catalog(CATALOG)
                .build();
        return pipeline.resolve(IntentRequests.of("q"), Ticket.class);
    }

    private static List<Long> ids(DifferentialDatabase db, String dsl) {
        return resolve(db, dsl).items().stream().map(t -> t.id).toList();
    }

    @ParameterizedTest
    @EnumSource(DifferentialDatabase.class)
    void filtersCompareTheStoredOrdinal(DifferentialDatabase db) {
        assertThat(ids(db, "tickets | filter priority == \"high\"")).containsExactly(2L, 5L);
        assertThat(ids(db, "tickets | filter priority in (\"low\", \"medium\")")).containsExactly(1L, 3L);
        assertThat(ids(db, "tickets | filter priority != \"high\"")).containsExactly(1L, 3L, 4L);
        assertThat(ids(db, "tickets | filter priority ~ \"ed\"")).containsExactly(3L);
        assertThat(resolve(db, "tickets | filter priority == \"high\"").trace().query()).containsAnyOf(
                "\"priority\" = ?",
                "`priority` = ?");
    }

    @ParameterizedTest
    @EnumSource(DifferentialDatabase.class)
    void sortingAndGroupingUseTheConstantNames(DifferentialDatabase db) {
        assertThat(ids(db, "tickets | sort priority, id")).containsExactly(2L, 5L, 1L, 3L, 4L);
        assertThat(resolve(db, "tickets | group priority | count").aggregation().rows())
                .extracting(r -> r.group().getFirst(), AggregateRow::value)
                .containsExactlyInAnyOrder(
                        tuple("HIGH", new BigDecimal("2")),
                        tuple("LOW", new BigDecimal("1")),
                        tuple("MEDIUM", new BigDecimal("1")),
                        tuple(null, new BigDecimal("1")));
    }
}
