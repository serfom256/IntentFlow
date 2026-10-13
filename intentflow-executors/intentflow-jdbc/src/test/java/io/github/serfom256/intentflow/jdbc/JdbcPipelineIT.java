package io.github.serfom256.intentflow.jdbc;

import io.github.serfom256.intentflow.dialect.postgresql.PostgresDialect;
import io.github.serfom256.intentflow.core.api.IntentResponse;
import io.github.serfom256.intentflow.core.api.factories.IntentRequests;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import io.github.serfom256.intentflow.core.catalog.factories.ColumnNames;
import io.github.serfom256.intentflow.core.catalog.factories.FieldMappings;
import io.github.serfom256.intentflow.core.catalog.factories.LogicalFieldNames;
import io.github.serfom256.intentflow.core.catalog.factories.MaxTakes;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceDescriptors;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceNames;
import io.github.serfom256.intentflow.core.catalog.factories.SortKeys;
import io.github.serfom256.intentflow.core.catalog.factories.TableNames;
import io.github.serfom256.intentflow.core.catalog.factories.UriTemplates;
import io.github.serfom256.intentflow.core.pipeline.IntentPipeline;
import io.github.serfom256.intentflow.core.pipeline.factories.AstValidators;
import io.github.serfom256.intentflow.core.pipeline.factories.DslCaches;
import io.github.serfom256.intentflow.core.pipeline.factories.DslParsers;
import io.github.serfom256.intentflow.core.pipeline.factories.IntentPipelines;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.core.pipeline.factories.TextNormalizers;
import io.github.serfom256.intentflow.core.pipeline.factories.UriBuilders;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class JdbcPipelineIT {

    record Book(long id, String title, String authorName, BigDecimal price, boolean inStock) {
    }

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.4");

    private static final ServiceName BOOKS = ServiceNames.of("books");

    private static final ServiceDescriptor BOOKS_SERVICE = ServiceDescriptors.of(
            BOOKS, null, TableNames.of("book"),
            List.of(
                    FieldMappings.of(LogicalFieldNames.of("id"), ColumnNames.of("id"), true),
                    FieldMappings.of(LogicalFieldNames.of("title"), ColumnNames.of("title"), true),
                    FieldMappings.of(LogicalFieldNames.of("author"), ColumnNames.of("author_name"), true),
                    FieldMappings.of(LogicalFieldNames.of("price"), ColumnNames.of("price"), true),
                    FieldMappings.of(LogicalFieldNames.of("in_stock"), ColumnNames.of("in_stock"), true)),
            List.of(SortKeys.of(LogicalFieldNames.of("id"), SortKey.Direction.ASC)),
            MaxTakes.of(50),
            UriTemplates.of("/books/{{id}}"),
            Book.class);

    private static final ServiceCatalog CATALOG = ServiceCatalogs.of(BOOKS_SERVICE);

    private static JdbcClient jdbc;

    @BeforeAll
    static void seed() {
        jdbc = JdbcClient.create(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        jdbc.sql("""
                CREATE TABLE book (
                    id INTEGER PRIMARY KEY,
                    title VARCHAR(255),
                    author_name VARCHAR(255),
                    price NUMERIC(10,2),
                    in_stock BOOLEAN
                )
                """).update();
        jdbc.sql("""
                INSERT INTO book VALUES
                    (1, 'Dune',              'Frank Herbert',  12.99, true),
                    (2, 'Dune Messiah',      'Frank Herbert',   9.99, false),
                    (3, 'Neuromancer',       'William Gibson',  9.50, true),
                    (4, '100% Pure_Fiction', 'Anon',            5.00, true)
                """).update();
    }

    private static IntentResponse<Book> resolve(String dsl) {
        IntentPipeline pipeline = IntentPipelines.create(
                TextNormalizers.create(),
                DslCaches.create(),
                (text, catalog) -> DslSources.of(dsl),
                DslParsers.create(),
                AstValidators.create(),
                SqlBuilders.create(new PostgresDialect()),
                new JdbcQueryExecutor(jdbc),
                UriBuilders.create(),
                CATALOG);
        return pipeline.resolve(IntentRequests.of("q"), Book.class);
    }

    private static List<String> titles(IntentResponse<Book> response) {
        return response.items().stream().map(Book::title).toList();
    }

    @Test
    void containsIsCaseInsensitive() {
        IntentResponse<Book> response = resolve("books | filter title ~ \"DUNE\"");

        assertThat(titles(response)).containsExactly("Dune", "Dune Messiah");
        assertThat(response.uri().value()).isEqualTo("/books/1");
    }

    @Test
    void containsTreatsLikeMetacharactersLiterally() {
        assertThat(titles(resolve("books | filter title ~ \"100%\""))).containsExactly("100% Pure_Fiction");
        assertThat(titles(resolve("books | filter title ~ \"e_f\""))).containsExactly("100% Pure_Fiction");
    }

    @Test
    void numericAndBooleanBindsWorkWithSortAndTake() {
        IntentResponse<Book> response = resolve(
                "books | filter price < 10 and in_stock == true | sort price desc | take 1");

        assertThat(titles(response)).containsExactly("Neuromancer");
    }

    @Test
    void inListOrAndNotCompose() {
        IntentResponse<Book> response = resolve(
                "books | filter author in (\"Frank Herbert\", \"Anon\") and not (price > 10)");

        assertThat(titles(response)).containsExactly("Dune Messiah", "100% Pure_Fiction");
    }

    @Test
    void skipAndTakeMapWholeEntities() {
        IntentResponse<Book> response = resolve("books | sort id asc | skip 2");

        assertThat(response.items()).extracting(Book::id).containsExactly(3L, 4L);
        Book neuromancer = response.items().getFirst();
        assertThat(neuromancer.price()).isEqualByComparingTo(new BigDecimal("9.50"));
        assertThat(neuromancer.authorName()).isEqualTo("William Gibson");
        assertThat(neuromancer.inStock()).isTrue();
        assertThat(response.uri().value()).isEqualTo("/books/3");
    }

    @Test
    void textEqualityAndInAreCaseInsensitive() {
        assertThat(titles(resolve("books | filter author == \"FRANK HERBERT\"")))
                .containsExactly("Dune", "Dune Messiah");
        assertThat(titles(resolve("books | filter author in (\"william gibson\", \"anon\")")))
                .containsExactly("Neuromancer", "100% Pure_Fiction");
    }

    @Test
    void emptyResultReturnsTemplateWithoutPlaceholders() {
        IntentResponse<Book> response = resolve("books | filter price > 1000");

        assertThat(response.uri().value()).isEqualTo("/books");
        assertThat(response.items()).isEmpty();
    }

}
