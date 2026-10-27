package io.github.serfom256.intentflow.jpa;

import io.github.serfom256.intentflow.core.api.IntentResponse;
import io.github.serfom256.intentflow.core.api.factories.IntentRequests;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.NameResolver;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceDescriptors;
import io.github.serfom256.intentflow.core.pipeline.IntentPipeline;
import io.github.serfom256.intentflow.core.pipeline.factories.IntentPipelines;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.core.schema.SchemaFacts;
import io.github.serfom256.intentflow.core.schema.TableFacts;
import io.github.serfom256.intentflow.core.testing.TestDatabase;
import io.github.serfom256.intentflow.dialect.mysql.MySqlDatabase;
import io.github.serfom256.intentflow.dialect.postgresql.PostgresDatabase;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.Session;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class JpaRestrictionsIT {

    static List<TestDatabase> databases() {
        return List.of(PostgresDatabase.POSTGRES, MySqlDatabase.MYSQL);
    }

    @Entity
    @Table(name = "inh_item")
    @DiscriminatorValue("ITEM")
    @FilterDef(name = "minLevel", parameters = @ParamDef(name = "level", type = Integer.class), defaultCondition = "level >= :level")
    @Filter(name = "minLevel")
    @IntentService(value = "items", uri = "/items/{{id}}", defaultSort = "id")
    public static class Item {
        @Id
        @IntentField
        Long id;
        @IntentField
        Integer level;
    }

    @Entity
    @DiscriminatorValue("BOOK")
    @IntentService(value = "books", uri = "/books/{{id}}", defaultSort = "id")
    public static class Book extends Item {
    }

    @Entity(name = "Film")
    @IntentService(value = "films", uri = "/films/{{id}}", defaultSort = "id")
    public static class Film extends Item {
    }

    @Entity
    public static class ShortFilm extends Film {
    }

    private static final List<Class<?>> ENTITIES = List.of(Item.class, Book.class, Film.class, ShortFilm.class);
    private static final List<NameResolver> RESOLVERS = List.of(new JpaNameResolver(() -> ENTITIES));
    private static final List<Class<?>> SERVICES = List.of(Item.class, Book.class, Film.class);
    private static final ServiceCatalog CATALOG = ServiceCatalogs
            .of(SERVICES.stream().map(t -> ServiceDescriptors.fromAnnotated(t, RESOLVERS)).toList());

    private static final Map<TestDatabase, EntityManager> MANAGERS = new HashMap<>();

    private static SchemaFacts facts() {
        Map<String, TableFacts> tables = new HashMap<>();
        tables.put("inh_item", TableFacts.primaryKey(List.of("id")));
        return SchemaFacts.of(tables);
    }

    private static synchronized EntityManager manager(TestDatabase db) {
        return MANAGERS.computeIfAbsent(db, d -> {
            TestDatabase.Endpoint e = d.endpoint();
            try (Connection c = DriverManager.getConnection(e.url(), e.user(), e.password());
                    Statement s = c.createStatement()) {
                s.execute("DROP TABLE IF EXISTS inh_item");
                s.execute("CREATE TABLE inh_item (id BIGINT PRIMARY KEY, DTYPE VARCHAR(31) NOT NULL, level INT)");
                s.execute("INSERT INTO inh_item VALUES (1, 'ITEM', 1), (2, 'BOOK', 2), (3, 'BOOK', 3), "
                        + "(4, 'Film', 1), (5, 'JpaRestrictionsIT$ShortFilm', 3)");
            } catch (SQLException ex) {
                throw new IllegalStateException(ex);
            }
            return TestEntityManagers
                    .create(e.url(), e.user(), e.password(), Item.class, Book.class, Film.class, ShortFilm.class)
                    .createEntityManager();
        });
    }

    private static <T> IntentResponse<T> resolve(TestDatabase db, String dsl, Class<T> type) {
        manager(db).clear();
        IntentPipeline pipeline = IntentPipelines.builder().llm((t, c) -> DslSources.of(dsl))
                .sqlBuilder(SqlBuilders.create(db.dialect(), facts())).executor(new JpaQueryExecutor(manager(db)))
                .catalog(CATALOG).build();
        return pipeline.resolve(IntentRequests.of("q"), type);
    }

    private static List<Long> ids(TestDatabase db, String dsl, Class<? extends Item> type) {
        return resolve(db, dsl, type).items().stream().map(i -> ((Item) i).id).toList();
    }

    @ParameterizedTest
    @MethodSource("databases")
    void aSubclassServiceSeesOnlyItsOwnRowsAndThoseOfItsSubclasses(TestDatabase db) {
        assertThat(ids(db, "items", Item.class)).containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(ids(db, "books", Book.class)).containsExactly(2L, 3L);
        assertThat(ids(db, "films", Film.class)).containsExactly(4L, 5L);
        assertThat(resolve(db, "films | count", Film.class).aggregation().orElseThrow().rows().getFirst().value())
                .hasToString("2");
    }

    @ParameterizedTest
    @MethodSource("databases")
    void filtersEnabledOnTheSessionApplyWithTheirParameters(TestDatabase db) {
        Session session = manager(db).unwrap(Session.class);
        session.enableFilter("minLevel").setParameter("level", 3);
        try {
            assertThat(ids(db, "items", Item.class)).containsExactly(3L, 5L);
            assertThat(ids(db, "books | filter level > 0", Book.class)).containsExactly(3L);
            assertThat(resolve(db, "items", Item.class).trace().query()).contains("(level >= ?)");
        } finally {
            session.disableFilter("minLevel");
        }
        assertThat(ids(db, "items", Item.class)).hasSize(5);
    }
}
