package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.api.IntentContext;
import io.github.serfom256.intentflow.core.api.UserText;
import io.github.serfom256.intentflow.core.api.factories.IntentContexts;
import io.github.serfom256.intentflow.core.api.factories.IntentRequests;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.FieldMapping;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.catalog.ServiceNotFoundException;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentRelation;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceNames;
import io.github.serfom256.intentflow.core.pipeline.PipelineListener.Outcome;
import io.github.serfom256.intentflow.core.pipeline.factories.IntentPipelines;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.core.testing.TestDialect;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessPolicyTest {

    @IntentService(value = "orders", table = "orders", uri = "/orders/{{id}}", defaultSort = "margin desc")
    @IntentRelation(name = "customer", service = "customers", from = "customer_id", to = "id")
    record Order(@IntentField long id, @IntentField(name = "customer_id") long customerId,
            @IntentField BigDecimal total, @IntentField BigDecimal margin) {
    }

    @IntentService(value = "customers", table = "customer", uri = "/customers/{{id}}", defaultSort = "id")
    @IntentRelation(name = "orders", service = "orders", from = "id", to = "customer_id")
    record Customer(@IntentField long id, @IntentField String name) {
    }

    @IntentService(value = "suppliers", table = "supplier", uri = "/suppliers/{{id}}", defaultSort = "id")
    record Supplier(@IntentField long id, @IntentField String name) {
    }

    private static final ServiceCatalog CATALOG = ServiceCatalogs.fromAnnotated(Order.class, Customer.class,
            Supplier.class);

    private static final IntentContext ANN = IntentContexts.of("ann", "acme", Set.of("ROLE_CUSTOMER"),
            Map.of("customerId", 7L));
    private static final IntentContext BUYER = IntentContexts.of("bob", "acme", Set.of("ROLE_BUYER"));

    private static final AccessPolicy POLICY = new AccessPolicy() {
        @Override
        public boolean canQuery(ServiceDescriptor service, IntentContext caller) {
            return !service.name().value().equals("suppliers") || caller.hasRole("ROLE_BUYER");
        }

        @Override
        public boolean canSee(ServiceDescriptor service, FieldMapping field, IntentContext caller) {
            return !field.logical().value().equals("margin") || caller.hasRole("ROLE_BUYER");
        }

        @Override
        public List<RuntimeRestriction> restrictions(ServiceDescriptor service, IntentContext caller) {
            return service.name().value().equals("orders") && !caller.hasRole("ROLE_BUYER")
                    ? List.of(RuntimeRestriction.equalTo("customer_id", caller.attribute("customerId")))
                    : List.of();
        }

        @Override
        public Optional<String> scope(ServiceDescriptor service, IntentContext caller) {
            return service.name().value().equals("orders") && !caller.hasRole("ROLE_BUYER")
                    ? Optional.of("only the current customer's own orders")
                    : Optional.empty();
        }
    };

    private final List<ServiceCatalog> shown = new ArrayList<>();
    private final List<BoundQuery> executed = new ArrayList<>();
    private final List<String> events = new ArrayList<>();

    private IntentPipeline pipeline(IntentContext defaultCaller, String... answers) {
        Deque<String> queue = new ArrayDeque<>(List.of(answers));
        LlmClient llm = (UserText text, ServiceCatalog catalog) -> {
            shown.add(catalog);
            return DslSources.of(queue.poll());
        };
        QueryExecutor executor = new QueryExecutor() {
            @Override
            public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
                executed.add(query);
                return List.of();
            }
        };
        PipelineListener listener = new PipelineListener() {
            @Override
            public void cache(boolean hit) {
                events.add(hit ? "cache hit" : "cache miss");
            }

            @Override
            public void llm(boolean repair, Duration elapsed, boolean failed) {
                events.add("llm");
            }

            @Override
            public void query(ServiceName service, boolean aggregate, Duration elapsed, int rows, boolean failed) {
                events.add("query " + service.value() + " " + rows);
            }

            @Override
            public void request(@Nullable ServiceName service, Outcome outcome, Duration elapsed) {
                events.add((service == null ? "-" : service.value()) + " " + outcome);
            }
        };
        return IntentPipelines.builder().llm(llm).sqlBuilder(SqlBuilders.create(new TestDialect())).executor(executor)
                .catalog(CATALOG).maxRepairs(0).accessPolicy(POLICY).defaultCaller(() -> defaultCaller)
                .listener(listener).build();
    }

    @Test
    void theLlmSeesOnlyWhatTheCallerMayQuery() {
        pipeline(ANN, "orders").resolve(IntentRequests.of("orders"));

        ServiceCatalog catalog = shown.getFirst();
        assertThat(catalog.names()).extracting(ServiceName::value).containsExactlyInAnyOrder("orders", "customers");
        assertThat(catalog.find(ServiceNames.of("orders")).orElseThrow().fields()).extracting(f -> f.logical().value())
                .containsExactly("id", "customer_id", "total");
    }

    @Test
    void hiddenServicesAndFieldsAreRejectedEvenWhenTheLlmUsesThem() {
        assertThatThrownBy(() -> pipeline(ANN, "suppliers").resolve(IntentRequests.of("suppliers")))
                .isInstanceOf(ServiceNotFoundException.class);
        assertThatThrownBy(() -> pipeline(ANN, "orders | filter margin > 5").resolve(IntentRequests.of("margins")))
                .isInstanceOf(ValidationException.class).hasMessageContaining("margin");
        assertThat(executed).isEmpty();
    }

    @Test
    void aServiceTheCallerMayNotQueryIsDeniedForTypedRequests() {
        IntentPipeline pipeline = pipeline(ANN);

        assertThatThrownBy(() -> pipeline.resolve(IntentRequests.of("suppliers"), Supplier.class))
                .isInstanceOf(AccessDeniedException.class).hasMessageContaining("suppliers");
        assertThat(events).containsExactly("suppliers DENIED");
    }

    @Test
    void restrictionsLimitEveryQueryOfTheServiceIncludingRelationSubqueries() {
        IntentPipeline pipeline = pipeline(ANN, "orders | filter total > 10", "customers | filter orders.total > 10");

        pipeline.resolve(IntentRequests.of("my big orders"));
        pipeline.resolve(IntentRequests.of("customers with big orders"));

        assertThat(executed.get(0).sql()).contains("(customer_id = ?)");
        assertThat(executed.get(0).values()).contains(7L);
        assertThat(executed.get(1).sql()).contains("FROM \"orders\"").contains("(customer_id = ?)");
        assertThat(executed.get(1).values()).contains(7L);
    }

    @Test
    void hiddenFieldsStillOrderResultsByTheServiceDefault() {
        pipeline(ANN, "orders").resolve(IntentRequests.of("orders"));

        assertThat(executed.getFirst().sql()).contains("ORDER BY \"margin\" DESC");
    }

    @Test
    void aMissingRestrictionValueMatchesNoRows() {
        IntentContext stranger = IntentContexts.of("eve", null, Set.of());

        pipeline(stranger, "orders").resolve(IntentRequests.of("orders"));

        assertThat(executed.getFirst().sql()).contains("(1 = 0)");
    }

    @Test
    void theScopeTellsTheLlmWhichRowsTheCallerSees() {
        pipeline(ANN, "orders").resolve(IntentRequests.of("my orders"));

        assertThat(shown.getFirst().find(ServiceNames.of("orders")).orElseThrow().description().orElseThrow().value())
                .contains("only the current customer's own orders").contains("\"my\"");
    }

    @Test
    void theRequestsContextWinsOverTheDefaultCaller() {
        IntentPipeline pipeline = pipeline(ANN, "orders");

        pipeline.resolve(IntentRequests.of("orders", BUYER));

        assertThat(executed.getFirst().sql()).doesNotContain("customer_id = ?");
        assertThat(shown.getFirst().names()).extracting(ServiceName::value).contains("suppliers");
    }

    @Test
    void translationsAreCachedPerView() {
        IntentPipeline pipeline = pipeline(ANN, "orders", "orders");

        pipeline.resolve(IntentRequests.of("orders"));
        pipeline.resolve(IntentRequests.of("orders"));
        pipeline.resolve(IntentRequests.of("orders", BUYER));

        assertThat(shown).hasSize(2);
        assertThat(events).containsExactly("cache miss", "llm", "query orders 0", "orders ANSWERED", "cache hit",
                "query orders 0", "orders ANSWERED", "cache miss", "llm", "query orders 0", "orders ANSWERED");
    }
}
