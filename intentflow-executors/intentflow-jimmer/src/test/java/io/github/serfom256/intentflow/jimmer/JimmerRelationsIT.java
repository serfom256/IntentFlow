package io.github.serfom256.intentflow.jimmer;

import io.github.serfom256.intentflow.core.api.factories.IntentRequests;
import io.github.serfom256.intentflow.core.ast.factories.BoundQueries;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.ServiceRelation;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceDescriptors;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceNames;
import io.github.serfom256.intentflow.core.pipeline.IntentPipeline;
import io.github.serfom256.intentflow.core.pipeline.factories.IntentPipelines;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.core.testing.RelationTables;
import io.github.serfom256.intentflow.core.testing.TestDatabase;
import io.github.serfom256.intentflow.dialect.mysql.MySqlDatabase;
import io.github.serfom256.intentflow.dialect.postgresql.PostgresDatabase;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.dialect.MySqlDialect;
import org.babyfish.jimmer.sql.dialect.PostgresDialect;
import org.babyfish.jimmer.sql.runtime.ConnectionManager;
import org.babyfish.jimmer.sql.runtime.DefaultDatabaseNamingStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@Testcontainers(disabledWithoutDocker = true)
class JimmerRelationsIT {

    static List<TestDatabase> databases() {
        return List.of(PostgresDatabase.POSTGRES, MySqlDatabase.MYSQL);
    }

    private static final ServiceCatalog CATALOG = ServiceCatalogs.of(
            ServiceDescriptors.fromAnnotated(JCustomer.class, List.of(new JimmerNameResolver())),
            ServiceDescriptors.fromAnnotated(JOrder.class, List.of(new JimmerNameResolver())));

    private static final Map<TestDatabase, JimmerQueryExecutor> EXECUTORS = new HashMap<>();

    private static synchronized JimmerQueryExecutor executor(TestDatabase db) {
        return EXECUTORS.computeIfAbsent(db, d -> {
            TestDatabase.Endpoint e = RelationTables.prepare(d);
            JSqlClient sqlClient = JSqlClient.newBuilder()
                    .setConnectionManager(ConnectionManager.simpleConnectionManager(
                            new DriverManagerDataSource(e.url(), e.user(), e.password())))
                    .setDialect(d == MySqlDatabase.MYSQL ? new MySqlDialect() : new PostgresDialect())
                    .setDatabaseNamingStrategy(DefaultDatabaseNamingStrategy.LOWER_CASE)
                    .build();
            return new JimmerQueryExecutor(sqlClient, List.of(
                    JOrderFetcher.$.allScalarFields().customer(JCustomerFetcher.$.allScalarFields())));
        });
    }

    private static IntentPipeline answering(TestDatabase db, String dsl) {
        return IntentPipelines.builder()
                .llm((t, c) -> DslSources.of(dsl))
                .sqlBuilder(SqlBuilders.create(db.dialect()))
                .executor(executor(db))
                .catalog(CATALOG)
                .build();
    }

    @Test
    void relationsComeFromJimmerAssociations() {
        assertThat(CATALOG.get(ServiceNames.of("orders")).relations())
                .extracting(ServiceRelation::name, r -> r.from().value(), r -> r.to().value())
                .containsExactly(tuple("customer", "customer_id", "id"));
        assertThat(CATALOG.get(ServiceNames.of("customers")).relations())
                .extracting(ServiceRelation::name, r -> r.from().value(), r -> r.to().value())
                .containsExactly(tuple("orders", "id", "customer_id"));
    }

    @ParameterizedTest
    @MethodSource("databases")
    void fetcherLoadsTheAssociationWithTheResults(TestDatabase db) {
        List<JOrder> orders = answering(db, "orders | filter customer.vip == true and status == \"paid\"")
                .resolve(IntentRequests.of("q"), JOrder.class).items();

        assertThat(orders).extracting(JOrder::id).containsExactly(11L);
        assertThat(orders.getFirst().customer().name()).isEqualTo("Ann");
    }

    @Test
    void theStatementTimeoutCancelsASlowQuery() {
        TestDatabase.Endpoint e = RelationTables.prepare(PostgresDatabase.POSTGRES);
        JSqlClient sqlClient = JSqlClient.newBuilder()
                .setConnectionManager(ConnectionManager.simpleConnectionManager(
                        new DriverManagerDataSource(e.url(), e.user(), e.password())))
                .setDialect(new PostgresDialect())
                .build();
        JimmerQueryExecutor executor = new JimmerQueryExecutor(sqlClient, List.of(), Duration.ofSeconds(1));
        long start = System.nanoTime();

        assertThatThrownBy(() -> executor.execute(
                BoundQueries.of("SELECT c.id FROM rel_customer c, pg_sleep(5)"), JCustomer.class))
                .hasMessageContaining("canceling statement");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(4));
    }

    @ParameterizedTest
    @MethodSource("databases")
    void filtersThroughOneToMany(TestDatabase db) {
        assertThat(answering(db, "customers | filter orders.total > 100 and orders.status == \"PAID\"")
                .resolve(IntentRequests.of("q"), JCustomer.class).items())
                .extracting(JCustomer::id).containsExactly(2L, 3L);
    }
}
