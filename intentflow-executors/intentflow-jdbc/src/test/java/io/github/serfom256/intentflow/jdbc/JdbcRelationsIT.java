package io.github.serfom256.intentflow.jdbc;

import io.github.serfom256.intentflow.core.api.AggregateRow;
import io.github.serfom256.intentflow.core.api.IntentResponse;
import io.github.serfom256.intentflow.core.api.factories.IntentRequests;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentRelation;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceDescriptors;
import io.github.serfom256.intentflow.core.pipeline.IntentPipeline;
import io.github.serfom256.intentflow.core.pipeline.ValidationException;
import io.github.serfom256.intentflow.core.pipeline.factories.IntentPipelines;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.core.testing.DifferentialDatabase;
import io.github.serfom256.intentflow.core.testing.RelationTables;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@Testcontainers(disabledWithoutDocker = true)
class JdbcRelationsIT {

    @IntentService(value = "customers", table = "rel_customer", uri = "/customers/{{id}}", defaultSort = "id")
    @IntentRelation(name = "orders", service = "orders", from = "id", to = "customer_id")
    record Customer(@IntentField long id, @IntentField String name, @IntentField String country,
            @IntentField boolean vip) {
    }

    @IntentService(value = "orders", table = "rel_order", uri = "/orders/{{id}}", defaultSort = "id")
    @IntentRelation(name = "customer", service = "customers", from = "customer_id", to = "id")
    record Order(@IntentField long id, @IntentField long customerId, @IntentField String status,
            @IntentField BigDecimal total) {
    }

    private static final Map<DifferentialDatabase, JdbcQueryExecutor> EXECUTORS = new EnumMap<>(
            DifferentialDatabase.class);

    private static synchronized JdbcQueryExecutor executor(DifferentialDatabase database) {
        return EXECUTORS.computeIfAbsent(database, db -> {
            DifferentialDatabase.Endpoint e = RelationTables.prepare(db);
            JdbcClient jdbc = JdbcClient.create(new DriverManagerDataSource(e.url(), e.user(), e.password()));
            return new JdbcQueryExecutor(jdbc);
        });
    }

    private static IntentPipeline answering(DifferentialDatabase database, String dsl) {
        return IntentPipelines.builder()
                .llm((text, catalog) -> DslSources.of(dsl))
                .sqlBuilder(SqlBuilders.create(database.dialect()))
                .executor(executor(database))
                .catalog(ServiceCatalogs.of(ServiceDescriptors.fromAnnotated(Customer.class),
                        ServiceDescriptors.fromAnnotated(Order.class)))
                .build();
    }

    private static List<Long> customers(DifferentialDatabase db, String dsl) {
        return answering(db, dsl).resolve(IntentRequests.of("q"), Customer.class).items().stream()
                .map(Customer::id).toList();
    }

    @ParameterizedTest
    @EnumSource(DifferentialDatabase.class)
    void manyToOneFiltersOnTheRelatedRow(DifferentialDatabase db) {
        IntentResponse<Order> orders = answering(db, "orders | filter customer.vip == true and status == \"paid\"")
                .resolve(IntentRequests.of("q"), Order.class);

        assertThat(orders.items()).extracting(Order::id).containsExactly(11L);
        assertThat(orders.trace().query()).contains("EXISTS (SELECT 1 FROM");
    }

    @ParameterizedTest
    @EnumSource(DifferentialDatabase.class)
    void conditionsOnOneRelationInAnAndMustHoldForTheSameRow(DifferentialDatabase db) {
        assertThat(customers(db, "customers | filter orders.total > 100 and orders.status == \"PAID\""))
                .containsExactly(2L, 3L);
    }

    @ParameterizedTest
    @EnumSource(DifferentialDatabase.class)
    void notMeansNoRelatedRowMatches(DifferentialDatabase db) {
        assertThat(customers(db, "customers | filter not (orders.status == \"CANCELLED\")"))
                .containsExactly(1L, 2L, 4L);
    }

    @ParameterizedTest
    @EnumSource(DifferentialDatabase.class)
    void relationsMixWithOwnFieldsInOr(DifferentialDatabase db) {
        assertThat(customers(db, "customers | filter orders.total >= 300 or (vip == true and country == \"fr\")"))
                .containsExactly(3L, 4L);
    }

    @ParameterizedTest
    @EnumSource(DifferentialDatabase.class)
    void aggregatesWorkOverRelationFilters(DifferentialDatabase db) {
        IntentResponse<Order> response = answering(db,
                "orders | filter customer.country == \"DE\" | group status | sum total")
                .resolve(IntentRequests.of("q"), Order.class);

        assertThat(response.items()).isEmpty();
        assertThat(response.aggregation().rows())
                .extracting(AggregateRow::group, r -> ((BigDecimal) r.value()).setScale(2))
                .containsExactly(
                        tuple(List.of("PAID"), new BigDecimal("200.00")),
                        tuple(List.of("NEW"), new BigDecimal("150.00")));
    }

    @ParameterizedTest
    @EnumSource(DifferentialDatabase.class)
    void sortsByAManyToOneField(DifferentialDatabase db) {
        assertThat(answering(db, "orders | sort customer.name desc").resolve(IntentRequests.of("q"), Order.class)
                .items()).extracting(Order::id).containsExactly(13L, 14L, 12L, 10L, 11L);
    }

    @ParameterizedTest
    @EnumSource(DifferentialDatabase.class)
    void sortsByTheLargestOrSmallestRelatedValueOfAOneToMany(DifferentialDatabase db) {
        assertThat(customers(db, "customers | sort orders.total desc")).containsExactly(3L, 1L, 2L, 4L);
        assertThat(customers(db, "customers | sort orders.total")).containsExactly(3L, 1L, 2L, 4L);
    }

    @ParameterizedTest
    @EnumSource(DifferentialDatabase.class)
    void relatedFieldsCannotBeGroupedBy(DifferentialDatabase db) {
        assertThatThrownBy(() -> answering(db, "orders | group customer.country | count")
                .resolve(IntentRequests.of("q"), Order.class))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("related field 'customer.country'");
    }

    private static List<Long> orders(DifferentialDatabase db, String dsl) {
        return answering(db, dsl).resolve(IntentRequests.of("q"), Order.class).items().stream()
                .map(Order::id).toList();
    }

    @ParameterizedTest
    @EnumSource(DifferentialDatabase.class)
    void keepsTheTopRowsOfEachPartition(DifferentialDatabase db) {
        assertThat(orders(db, "orders | top 1 per customer_id by total desc")).containsExactly(10L, 12L, 14L);
        assertThat(orders(db, "orders | filter status == \"PAID\" | top 1 per customer_id by total | sort total desc"))
                .containsExactly(14L, 12L, 11L);
    }

    @ParameterizedTest
    @EnumSource(DifferentialDatabase.class)
    void topWithoutPartitionBreaksTiesByTheDefaultSort(DifferentialDatabase db) {
        assertThat(orders(db, "orders | top 2 by total desc")).containsExactly(10L, 14L);
    }

    @ParameterizedTest
    @EnumSource(DifferentialDatabase.class)
    void topCombinesWithRelationFilters(DifferentialDatabase db) {
        assertThat(orders(db, "orders | filter customer.country == \"DE\" | top 1 per customer_id by total"))
                .containsExactly(11L, 12L);
    }
}
