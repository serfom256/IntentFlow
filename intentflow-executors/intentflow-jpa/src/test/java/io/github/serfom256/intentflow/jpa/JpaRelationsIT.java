package io.github.serfom256.intentflow.jpa;

import io.github.serfom256.intentflow.core.api.IntentResponse;
import io.github.serfom256.intentflow.core.api.factories.IntentRequests;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.ServiceRelation;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceDescriptors;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceNames;
import io.github.serfom256.intentflow.core.pipeline.IntentPipeline;
import io.github.serfom256.intentflow.core.pipeline.factories.IntentPipelines;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.core.schema.SchemaFacts;
import io.github.serfom256.intentflow.core.testing.DifferentialDatabase;
import io.github.serfom256.intentflow.core.testing.RelationTables;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.NamedAttributeNode;
import jakarta.persistence.NamedEntityGraph;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.SQLRestriction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@Testcontainers(disabledWithoutDocker = true)
class JpaRelationsIT {

    @Entity
    @Table(name = "rel_customer")
    @IntentService(value = "customers", uri = "/customers/{{id}}", defaultSort = "id")
    public static class Customer {
        @Id
        @IntentField
        Long id;
        @IntentField
        String name;
        @IntentField
        String country;
        @IntentField
        Boolean vip;
        @OneToMany(mappedBy = "customer")
        List<Order> orders;
    }

    @Entity
    @Table(name = "rel_order")
    @NamedEntityGraph(name = "Order.intentflow", attributeNodes = @NamedAttributeNode("customer"))
    @IntentService(value = "orders", uri = "/orders/{{id}}", defaultSort = "id")
    public static class Order {
        @Id
        @IntentField
        Long id;
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "customer_id")
        Customer customer;
        @IntentField
        String status;
        @IntentField
        BigDecimal total;
        @Formula("total * 2")
        BigDecimal doubled;
    }

    @Entity
    @Table(name = "rel_order")
    @SQLRestriction("status <> 'CANCELLED'")
    @IntentService(value = "active_orders", uri = "/orders/{{id}}", defaultSort = "id")
    public static class ActiveOrder {
        @Id
        @IntentField
        Long id;
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "customer_id")
        Customer customer;
        @IntentField
        String status;
        @IntentField
        BigDecimal total;
    }

    private static final Map<DifferentialDatabase, EntityManager> MANAGERS = new EnumMap<>(DifferentialDatabase.class);

    private static final ServiceCatalog CATALOG = ServiceCatalogs.of(
            ServiceDescriptors.fromAnnotated(Customer.class, List.of(new JpaNameResolver())),
            ServiceDescriptors.fromAnnotated(Order.class, List.of(new JpaNameResolver())),
            ServiceDescriptors.fromAnnotated(ActiveOrder.class, List.of(new JpaNameResolver())));

    private static final SchemaFacts FACTS = SchemaFacts.of(Map.of(
            "rel_order", ServiceDescriptors.factsOf(Order.class, List.of(new JpaNameResolver())),
            "rel_customer", ServiceDescriptors.factsOf(Customer.class, List.of(new JpaNameResolver()))),
            Map.of(ServiceNames.of("active_orders"), ServiceDescriptors
                    .restrictionOf(ActiveOrder.class, List.of(new JpaNameResolver())).orElseThrow()));

    private static synchronized EntityManager manager(DifferentialDatabase db) {
        return MANAGERS.computeIfAbsent(db, d -> {
            DifferentialDatabase.Endpoint e = RelationTables.prepare(d);
            EntityManagerFactory emf = TestEntityManagers.create(e.url(), e.user(), e.password(), Customer.class,
                    Order.class, ActiveOrder.class);
            return emf.createEntityManager();
        });
    }

    private static IntentPipeline answering(DifferentialDatabase db, String dsl) {
        manager(db).clear();
        return IntentPipelines.builder()
                .llm((t, c) -> DslSources.of(dsl))
                .sqlBuilder(SqlBuilders.create(db.dialect(), FACTS))
                .executor(new JpaQueryExecutor(manager(db)))
                .catalog(CATALOG)
                .build();
    }

    @Test
    void relationsComeFromJpaAssociationsWithoutIntentRelation() {
        ServiceDescriptor orders = CATALOG.get(ServiceNames.of("orders"));
        ServiceDescriptor customers = CATALOG.get(ServiceNames.of("customers"));

        assertThat(orders.relations()).extracting(ServiceRelation::name, r -> r.target().value(),
                r -> r.from().value(), r -> r.to().value())
                .containsExactly(tuple("customer", "customers", "customer_id", "id"));
        assertThat(customers.relations()).extracting(ServiceRelation::name, r -> r.from().value(), r -> r.to().value())
                .containsExactly(tuple("orders", "id", "customer_id"));
    }

    @ParameterizedTest
    @EnumSource(DifferentialDatabase.class)
    void filtersThroughManyToOneAndTheOrmLoadsTheAssociation(DifferentialDatabase db) {
        List<Order> orders = answering(db, "orders | filter customer.vip == true and status == \"paid\"")
                .resolve(IntentRequests.of("q"), Order.class).items();

        assertThat(orders).extracting(o -> o.id).containsExactly(11L);
        Order order = orders.getFirst();
        assertThat(manager(db).getEntityManagerFactory().getPersistenceUnitUtil().isLoaded(order, "customer")).isTrue();
        assertThat(order.customer.name).isEqualTo("Ann");
        assertThat(order.doubled).isEqualByComparingTo("100.00");
    }

    @ParameterizedTest
    @EnumSource(DifferentialDatabase.class)
    void filtersThroughOneToManyMappedBy(DifferentialDatabase db) {
        List<Customer> customers = answering(db, "customers | filter orders.total > 100 and orders.status == \"PAID\"")
                .resolve(IntentRequests.of("q"), Customer.class).items();

        assertThat(customers).extracting(c -> c.id).containsExactly(2L, 3L);
    }

    @ParameterizedTest
    @EnumSource(DifferentialDatabase.class)
    void hibernateRestrictionsApplyToListsCountsAndKeys(DifferentialDatabase db) {
        IntentResponse<ActiveOrder> active = answering(db, "active_orders | filter total < 100")
                .resolve(IntentRequests.of("q"), ActiveOrder.class);
        IntentResponse<Order> all = answering(db, "orders | filter total < 100")
                .resolve(IntentRequests.of("q"), Order.class);

        assertThat(active.items()).extracting(o -> o.id).containsExactly(11L);
        assertThat(all.items()).extracting(o -> o.id).containsExactly(11L, 13L);
        assertThat(active.trace().query()).contains("(status <> 'CANCELLED')").doesNotContain("*");
        assertThat(active.trace().optimizations()).contains("key-projection");
        assertThat(answering(db, "active_orders | count").resolve(IntentRequests.of("q"), ActiveOrder.class)
                .aggregation().rows().getFirst().value()).isEqualTo(new BigDecimal("4"));
    }
}
