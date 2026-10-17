package io.github.serfom256.intentflow.openmapper.relations;

import io.github.serfom256.annotations.entity.Field;
import io.github.serfom256.annotations.entity.Joined;
import io.github.serfom256.annotations.entity.Model;
import io.github.serfom256.intentflow.core.api.factories.IntentRequests;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.ServiceRelation;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceDescriptors;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceNames;
import io.github.serfom256.intentflow.core.pipeline.factories.AstValidators;
import io.github.serfom256.intentflow.core.pipeline.factories.DslCaches;
import io.github.serfom256.intentflow.core.pipeline.factories.DslParsers;
import io.github.serfom256.intentflow.core.pipeline.factories.IntentPipelines;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.core.pipeline.factories.TextNormalizers;
import io.github.serfom256.intentflow.core.pipeline.factories.UriBuilders;
import io.github.serfom256.intentflow.core.testing.DifferentialDatabase;
import io.github.serfom256.intentflow.core.testing.RelationTables;
import io.github.serfom256.intentflow.openmapper.OpenMapperNameResolver;
import io.github.serfom256.intentflow.openmapper.OpenMapperQueryExecutor;
import io.github.serfom256.procedures.Procedures;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenMapperRelationsIT {

    @Model(primaryKey = "id")
    @IntentService(value = "customers", table = "rel_customer", uri = "/customers/{{id}}", defaultSort = "id")
    public static class Customer {
        @Field
        @IntentField
        Long id;
        @Field
        @IntentField
        String name;
        @Field
        @IntentField
        String country;
        @Field
        @IntentField
        Boolean vip;
        @Joined(joinBy = "id", to = "customerId")
        List<Order> orders;
    }

    @Model(primaryKey = "id")
    @IntentService(value = "orders", table = "rel_order", uri = "/orders/{{id}}", defaultSort = "id")
    public static class Order {
        @Field
        @IntentField
        Long id;
        @Field(name = "customer_id")
        Long customerId;
        @Field
        @IntentField
        String status;
        @Field
        @IntentField
        BigDecimal total;
        @Joined(joinBy = "customerId", to = "id")
        Customer customer;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class App {
    }

    private static final ServiceCatalog CATALOG = ServiceCatalogs.of(
            ServiceDescriptors.fromAnnotated(Customer.class, List.of(new OpenMapperNameResolver())),
            ServiceDescriptors.fromAnnotated(Order.class, List.of(new OpenMapperNameResolver())));

    private ConfigurableApplicationContext context;
    private OpenMapperQueryExecutor executor;

    @BeforeAll
    void start() {
        DifferentialDatabase.Endpoint e = RelationTables.prepare(DifferentialDatabase.POSTGRES);
        context = new SpringApplicationBuilder(App.class)
                .web(WebApplicationType.NONE)
                .properties("spring.datasource.url=" + e.url(), "spring.datasource.username=" + e.user(),
                        "spring.datasource.password=" + e.password(),
                        "openmapper.model.packagesToScan=io.github.serfom256.intentflow.openmapper.relations")
                .run();
        executor = new OpenMapperQueryExecutor(context.getBean(Procedures.class));
    }

    @AfterAll
    void stop() {
        context.close();
    }

    private <T> List<T> resolve(String dsl, Class<T> type) {
        return IntentPipelines.create(TextNormalizers.create(), DslCaches.create(), (t, c) -> DslSources.of(dsl),
                DslParsers.create(), AstValidators.create(),
                SqlBuilders.create(DifferentialDatabase.POSTGRES.dialect()),
                executor, UriBuilders.create(), CATALOG).resolve(IntentRequests.of("q"), type).items();
    }

    @Test
    void relationsComeFromJoinedFields() {
        assertThat(CATALOG.get(ServiceNames.of("orders")).relations())
                .extracting(ServiceRelation::name, r -> r.from().value(), r -> r.to().value())
                .containsExactly(tuple("customer", "customer_id", "id"));
        assertThat(CATALOG.get(ServiceNames.of("customers")).relations())
                .extracting(ServiceRelation::name, r -> r.from().value(), r -> r.to().value())
                .containsExactly(tuple("orders", "id", "customer_id"));
    }

    @Test
    void filtersThroughJoinedRelationsInBothDirections() {
        assertThat(resolve("orders | filter customer.vip == true and status == \"paid\"", Order.class))
                .extracting(o -> o.id).containsExactly(11L);
        assertThat(resolve("customers | filter orders.total > 100 and orders.status == \"PAID\"", Customer.class))
                .extracting(c -> c.id).containsExactly(2L, 3L);
    }
}
