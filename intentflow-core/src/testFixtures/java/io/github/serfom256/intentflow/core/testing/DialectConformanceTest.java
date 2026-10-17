package io.github.serfom256.intentflow.core.testing;

import io.github.serfom256.intentflow.core.api.IntentResponse;
import io.github.serfom256.intentflow.core.api.factories.IntentRequests;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentRelation;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceDescriptors;
import io.github.serfom256.intentflow.core.pipeline.IntentPipeline;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import io.github.serfom256.intentflow.core.pipeline.factories.AstValidators;
import io.github.serfom256.intentflow.core.pipeline.factories.DslCaches;
import io.github.serfom256.intentflow.core.pipeline.factories.DslParsers;
import io.github.serfom256.intentflow.core.pipeline.factories.IntentPipelines;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.core.pipeline.factories.TextNormalizers;
import io.github.serfom256.intentflow.core.pipeline.factories.UriBuilders;
import io.github.serfom256.intentflow.core.schema.JdbcSchemaIntrospector;
import io.github.serfom256.intentflow.core.schema.SchemaFacts;
import io.github.serfom256.intentflow.core.schema.TableFacts;
import io.github.serfom256.intentflow.core.sql.Optimization;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class DialectConformanceTest extends ExecutorDifferentialTest {

    @IntentService(value = "gadgets", table = "gadget", uri = "/gadgets/{{id}}", maxTake = 500, defaultSort = "id")
    public record Gadget(
            @IntentField long id,
            @IntentField String name,
            @IntentField(caseSensitive = true) GadgetKind kind,
            @IntentField BigDecimal price,
            @IntentField Integer stock,
            @IntentField Boolean active,
            @IntentField LocalDateTime createdAt) {
    }

    @IntentService(value = "customers", table = "rel_customer", uri = "/customers/{{id}}", defaultSort = "id")
    @IntentRelation(name = "orders", service = "orders", from = "id", to = "customer_id")
    @IntentRelation(name = "notes", service = "notes", from = "id", to = "customer_id")
    public record Customer(@IntentField long id, @IntentField String name, @IntentField String country,
            @IntentField boolean vip) {
    }

    @IntentService(value = "orders", table = "rel_order", uri = "/orders/{{id}}", defaultSort = "id")
    @IntentRelation(name = "customer", service = "customers", from = "customer_id", to = "id")
    public record Order(@IntentField long id, @IntentField long customerId, @IntentField String status,
            @IntentField BigDecimal total) {
    }

    @IntentService(value = "notes", table = "conf_note", uri = "/notes/{{id}}", defaultSort = "id", restriction = "deleted = FALSE")
    @IntentRelation(name = "customer", service = "customers", from = "customer_id", to = "id")
    public record Note(@IntentField long id, @IntentField long customerId, @IntentField String text) {
    }

    @IntentService(value = "events", table = "conf_event", uri = "/events/{{id}}", defaultSort = "id")
    public record Event(@IntentField long id, @IntentField OffsetDateTime at) {
    }

    @Override
    protected abstract TestDatabase database();

    @Override
    protected Class<?> entityType() {
        return Gadget.class;
    }

    @Override
    protected QueryExecutor createExecutor(String jdbcUrl, String user, String password) {
        return new PlainJdbcExecutor(new TestDatabase.Endpoint(jdbcUrl, user, password));
    }

    private <T> List<Long> ids(String dsl, Class<T> type) {
        return resolve(dsl, type).items().stream().map(item -> switch (item) {
            case Order o -> o.id();
            case Customer c -> c.id();
            case Note n -> n.id();
            default -> throw new IllegalStateException(String.valueOf(item));
        }).toList();
    }

    private <T> IntentResponse<T> resolve(String dsl, Class<T> type) {
        TestDatabase.Endpoint endpoint = RelationTables.prepare(database());
        try (Connection c = DriverManager.getConnection(endpoint.url(), endpoint.user(), endpoint.password());
                Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS conf_note");
            s.execute("CREATE TABLE conf_note (id BIGINT PRIMARY KEY, customer_id BIGINT NOT NULL, "
                    + "text VARCHAR(40), deleted BOOLEAN NOT NULL)");
            s.execute("INSERT INTO conf_note VALUES (1, 1, 'call back', FALSE), (2, 2, 'call back', TRUE), "
                    + "(3, 3, 'invoice sent', FALSE)");
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        List<Class<?>> types = List.of(Customer.class, Order.class, Note.class);
        Map<String, TableFacts> facts = new HashMap<>();
        try {
            facts.putAll(JdbcSchemaIntrospector.introspect(new DriverManagerDataSource(endpoint),
                    List.of("rel_customer", "rel_order", "conf_note")));
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        ServiceCatalog catalog = ServiceCatalogs.of(types.stream().map(ServiceDescriptors::fromAnnotated).toList());
        Map<ServiceName, String> restrictions = new HashMap<>();
        for (Class<?> t : types) {
            ServiceDescriptor service = catalog.findByType(t).orElseThrow();
            facts.merge(service.table().value(), ServiceDescriptors.factsOf(t, List.of()), TableFacts::merge);
            ServiceDescriptors.restrictionOf(t, List.of()).ifPresent(sql -> restrictions.put(service.name(), sql));
        }
        IntentPipeline pipeline = IntentPipelines.create(TextNormalizers.create(), DslCaches.create(),
                (text, scope) -> DslSources.of(dsl), DslParsers.create(), AstValidators.create(),
                SqlBuilders.create(database().dialect(), SchemaFacts.of(facts, restrictions)),
                new PlainJdbcExecutor(endpoint),
                UriBuilders.create(), catalog);
        return pipeline.resolve(IntentRequests.of("q"), type);
    }

    @Test
    void filtersThroughRelationsInBothDirections() {
        assertThat(ids("orders | filter customer.vip == true and status == \"paid\"", Order.class))
                .containsExactly(11L);
        assertThat(ids("customers | filter orders.total > 100 and orders.status == \"PAID\"", Customer.class))
                .containsExactly(2L, 3L);
        assertThat(ids("customers | filter not (orders.status == \"CANCELLED\")", Customer.class))
                .containsExactly(1L, 2L, 4L);
    }

    @Test
    void sortsByRelatedFields() {
        assertThat(ids("orders | sort customer.name desc", Order.class)).containsExactly(13L, 14L, 12L, 10L, 11L);
        assertThat(ids("customers | sort orders.total desc", Customer.class)).containsExactly(3L, 1L, 2L, 4L);
    }

    @Test
    void sortingByAToOneRelationJoinsItsTable() {
        IntentResponse<Order> response = resolve("orders | sort customer.name desc", Order.class);

        assertThat(response.items()).extracting(Order::id).containsExactly(13L, 14L, 12L, 10L, 11L);
        assertThat(response.trace().optimizations()).contains("to-one-join");
        assertThat(response.trace().query()).contains("LEFT JOIN");
    }

    @Test
    void restrictionsApplyToTheServiceAndInsideRelations() {
        assertThat(ids("notes", Note.class)).containsExactly(1L, 3L);
        assertThat(ids("notes | filter text ~ \"call\"", Note.class)).containsExactly(1L);
        assertThat(ids("customers | filter notes.text ~ \"call\"", Customer.class)).containsExactly(1L);
        assertThat(ids("customers | filter not (notes.text ~ \"call\")", Customer.class))
                .containsExactly(2L, 3L, 4L);
    }

    private List<Long> events(String dsl, ZoneId zone) {
        TestDatabase.Endpoint endpoint = database().endpoint();
        boolean mysql = database().dialect().name().equals("mysql");
        try (Connection c = DriverManager.getConnection(endpoint.url(), endpoint.user(), endpoint.password());
                Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS conf_event");
            s.execute("CREATE TABLE conf_event (id BIGINT PRIMARY KEY, at "
                    + (mysql ? "TIMESTAMP NULL" : "TIMESTAMP WITH TIME ZONE") + ")");
            try (PreparedStatement insert = c.prepareStatement("INSERT INTO conf_event VALUES (?, ?)")) {
                List<String> instants = List.of("2026-10-09T21:30:00Z", "2026-10-09T22:30:00Z",
                        "2026-10-10T21:30:00Z", "2026-10-10T22:30:00Z");
                for (int i = 0; i < instants.size(); i++) {
                    insert.setLong(1, i + 1);
                    insert.setObject(2, OffsetDateTime.parse(instants.get(i)));
                    insert.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        Map<String, TableFacts> facts = new HashMap<>();
        try {
            facts.putAll(JdbcSchemaIntrospector.introspect(new DriverManagerDataSource(endpoint),
                    List.of("conf_event")));
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        IntentPipeline pipeline = IntentPipelines.create(TextNormalizers.create(), DslCaches.create(),
                (text, scope) -> DslSources.of(dsl), DslParsers.create(), AstValidators.create(),
                SqlBuilders.create(database().dialect(), SchemaFacts.of(facts, Map.of()),
                        EnumSet.allOf(Optimization.class), zone),
                new PlainJdbcExecutor(endpoint), UriBuilders.create(), ServiceCatalogs.fromAnnotated(Event.class));
        return pipeline.resolve(IntentRequests.of("q"), Event.class).items().stream().map(Event::id).toList();
    }

    @Test
    void wholeDaysOfAZonedColumnFollowTheConfiguredZone() {
        assertThat(events("events | filter at == \"2026-10-10\"", ZoneId.of("Europe/Berlin"))).containsExactly(2L, 3L);
        assertThat(events("events | filter at == \"2026-10-10\"", ZoneOffset.UTC)).containsExactly(3L, 4L);
        assertThat(events("events | filter at >= \"2026-10-10T00:00:00+02:00\"", ZoneOffset.UTC))
                .containsExactly(2L, 3L, 4L);
    }

    @Test
    void keepsTheTopRowsOfEachPartition() {
        assertThat(ids("orders | top 1 per customer_id by total desc", Order.class)).containsExactly(10L, 12L, 14L);
        assertThat(ids("orders | filter customer.country == \"DE\" | top 1 per customer_id by total", Order.class))
                .containsExactly(11L, 12L);
    }
}
