package io.github.serfom256.intentflow.jimmer;

import io.github.serfom256.intentflow.core.api.factories.IntentRequests;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.ServiceRelation;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceDescriptors;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceNames;
import io.github.serfom256.intentflow.core.pipeline.IntentPipeline;
import io.github.serfom256.intentflow.core.pipeline.factories.AstValidators;
import io.github.serfom256.intentflow.core.pipeline.factories.DslCaches;
import io.github.serfom256.intentflow.core.pipeline.factories.DslParsers;
import io.github.serfom256.intentflow.core.pipeline.factories.IntentPipelines;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.core.pipeline.factories.TextNormalizers;
import io.github.serfom256.intentflow.core.pipeline.factories.UriBuilders;
import io.github.serfom256.intentflow.core.testing.DifferentialDatabase;
import io.github.serfom256.intentflow.core.testing.RelationTables;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.dialect.MySqlDialect;
import org.babyfish.jimmer.sql.dialect.PostgresDialect;
import org.babyfish.jimmer.sql.runtime.ConnectionManager;
import org.babyfish.jimmer.sql.runtime.DefaultDatabaseNamingStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@Testcontainers(disabledWithoutDocker = true)
class JimmerRelationsIT {

    private static final ServiceCatalog CATALOG = ServiceCatalogs.of(
            ServiceDescriptors.fromAnnotated(JCustomer.class, List.of(new JimmerNameResolver())),
            ServiceDescriptors.fromAnnotated(JOrder.class, List.of(new JimmerNameResolver())));

    private static final Map<DifferentialDatabase, JimmerQueryExecutor> EXECUTORS = new EnumMap<>(
            DifferentialDatabase.class);

    private static synchronized JimmerQueryExecutor executor(DifferentialDatabase db) {
        return EXECUTORS.computeIfAbsent(db, d -> {
            DifferentialDatabase.Endpoint e = RelationTables.prepare(d);
            JSqlClient sqlClient = JSqlClient.newBuilder()
                    .setConnectionManager(ConnectionManager.simpleConnectionManager(
                            new DriverManagerDataSource(e.url(), e.user(), e.password())))
                    .setDialect(d == DifferentialDatabase.MYSQL ? new MySqlDialect() : new PostgresDialect())
                    .setDatabaseNamingStrategy(DefaultDatabaseNamingStrategy.LOWER_CASE)
                    .build();
            return new JimmerQueryExecutor(sqlClient, List.of(
                    JOrderFetcher.$.allScalarFields().customer(JCustomerFetcher.$.allScalarFields())));
        });
    }

    private static IntentPipeline answering(DifferentialDatabase db, String dsl) {
        return IntentPipelines.create(TextNormalizers.create(), DslCaches.create(), (t, c) -> DslSources.of(dsl),
                DslParsers.create(), AstValidators.create(), SqlBuilders.create(db.dialect()), executor(db),
                UriBuilders.create(), CATALOG);
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
    @EnumSource(DifferentialDatabase.class)
    void fetcherLoadsTheAssociationWithTheResults(DifferentialDatabase db) {
        List<JOrder> orders = answering(db, "orders | filter customer.vip == true and status == \"paid\"")
                .resolve(IntentRequests.of("q"), JOrder.class).items();

        assertThat(orders).extracting(JOrder::id).containsExactly(11L);
        assertThat(orders.getFirst().customer().name()).isEqualTo("Ann");
    }

    @ParameterizedTest
    @EnumSource(DifferentialDatabase.class)
    void filtersThroughOneToMany(DifferentialDatabase db) {
        assertThat(answering(db, "customers | filter orders.total > 100 and orders.status == \"PAID\"")
                .resolve(IntentRequests.of("q"), JCustomer.class).items())
                .extracting(JCustomer::id).containsExactly(2L, 3L);
    }
}
