package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.api.IntentContext;
import io.github.serfom256.intentflow.core.api.factories.IntentContexts;
import io.github.serfom256.intentflow.core.api.factories.IntentRequests;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.FieldMapping;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentRelation;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.pipeline.factories.IntentPipelineBuilder;
import io.github.serfom256.intentflow.core.pipeline.factories.IntentPipelines;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.core.testing.TestDialect;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VisibleFieldsTest {

    @IntentService(value = "books", uri = "/books/{{id}}", defaultSort = "id")
    record Book(@IntentField long id, @IntentField String title, int stock, String supplier) {
    }

    @IntentService(value = "customers", uri = "/customers/{{id}}", defaultSort = "id")
    @IntentRelation(name = "orders", service = "orders", from = "id", to = "customer_id")
    static class Customer {
        @IntentField
        Long id;
        @IntentField
        String name;
        @IntentField
        BigDecimal creditLimit;
        String passwordHash;
        List<Order> orders = new ArrayList<>();
    }

    @IntentService(value = "orders", uri = "/orders/{{id}}", defaultSort = "id")
    static class Order {
        @IntentField
        Long id;
        @IntentField
        Long customerId;
        @IntentField
        BigDecimal total;
        String internalNote;
    }

    @IntentService(value = "tickets", uri = "/tickets/{{id}}", defaultSort = "id")
    static final class Ticket {
        @IntentField
        private final long id;
        @IntentField
        private final String subject;
        private final String reporterEmail;

        Ticket(long id, String subject, String reporterEmail) {
            this.id = id;
            this.subject = subject;
            this.reporterEmail = reporterEmail;
        }
    }

    @IntentService(value = "gadgets", uri = "/gadgets/{{id}}", defaultSort = "id")
    interface Gadget {
        @IntentField
        long id();
    }

    private static final IntentContext CLERK = IntentContexts.of("clerk", null, Set.of());

    private static IntentPipelineBuilder pipeline(ServiceCatalog catalog, String dsl, List<?> rows) {
        return IntentPipelines.builder()
                .llm((text, scope) -> DslSources.of(dsl))
                .sqlBuilder(SqlBuilders.create(new TestDialect()))
                .executor(new QueryExecutor() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
                        return (List<T>) rows;
                    }
                })
                .catalog(catalog)
                .visibleFieldsOnly(true);
    }

    @Test
    void fieldsWithoutIntentFieldComeBackEmpty() {
        Book stored = new Book(7, "Dune", 12, "Acme Books");

        Book book = pipeline(ServiceCatalogs.fromAnnotated(Book.class), "books", List.of(stored)).build()
                .resolve(IntentRequests.of("books"), Book.class).items().getFirst();

        assertThat(book).isEqualTo(new Book(7, "Dune", 0, null));
    }

    @Test
    void everythingComesBackWhenTheOptionIsOff() {
        Book stored = new Book(7, "Dune", 12, "Acme Books");

        Book book = pipeline(ServiceCatalogs.fromAnnotated(Book.class), "books", List.of(stored))
                .visibleFieldsOnly(false).build().resolve(IntentRequests.of("books"), Book.class).items()
                .getFirst();

        assertThat(book).isSameAs(stored);
    }

    @Test
    void fieldsThePolicyHidesComeBackEmptyAndRelationsAreFilteredToo() {
        Customer stored = new Customer();
        stored.id = 1L;
        stored.name = "Ann";
        stored.creditLimit = new BigDecimal("5000");
        stored.passwordHash = "$2a$10$secret";
        Order order = new Order();
        order.id = 11L;
        order.customerId = 1L;
        order.total = new BigDecimal("99.90");
        order.internalNote = "VIP, call before delivery";
        stored.orders.add(order);
        AccessPolicy noCredit = new AccessPolicy() {
            @Override
            public boolean canSee(ServiceDescriptor service, FieldMapping field, IntentContext caller) {
                return !field.logical().value().equals("credit_limit");
            }
        };

        Customer customer = pipeline(ServiceCatalogs.fromAnnotated(Customer.class, Order.class), "customers",
                List.of(stored)).accessPolicy(noCredit).build()
                .resolve(IntentRequests.of("customers", CLERK), Customer.class).items().getFirst();

        assertThat(customer).isNotSameAs(stored);
        assertThat(customer.id).isEqualTo(1L);
        assertThat(customer.name).isEqualTo("Ann");
        assertThat(customer.creditLimit).isNull();
        assertThat(customer.passwordHash).isNull();
        assertThat(customer.orders).singleElement().satisfies(copy -> {
            assertThat(copy.total).isEqualByComparingTo("99.90");
            assertThat(copy.internalNote).isNull();
        });
        assertThat(stored.passwordHash).isEqualTo("$2a$10$secret");
        assertThat(stored.orders.getFirst().internalNote).isNotNull();
    }

    @Test
    void classesWithFinalFieldsAreCopiedThroughTheirConstructor() {
        Ticket stored = new Ticket(3, "Printer jam", "ann@example.com");

        Ticket ticket = pipeline(ServiceCatalogs.fromAnnotated(Ticket.class), "tickets", List.of(stored)).build()
                .resolve(IntentRequests.of("tickets"), Ticket.class).items().getFirst();

        assertThat(ticket.id).isEqualTo(3);
        assertThat(ticket.subject).isEqualTo("Printer jam");
        assertThat(ticket.reporterEmail).isNull();
    }

    @Test
    void entitiesThatCannotBeCopiedFailAtBuild() {
        assertThatThrownBy(() -> pipeline(ServiceCatalogs.fromAnnotated(Gadget.class), "gadgets", List.of())
                .build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(Gadget.class.getName());
    }
}
