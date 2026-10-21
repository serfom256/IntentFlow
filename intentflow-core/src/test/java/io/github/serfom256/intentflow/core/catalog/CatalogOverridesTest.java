package io.github.serfom256.intentflow.core.catalog;

import io.github.serfom256.intentflow.core.api.IntentResponse;
import io.github.serfom256.intentflow.core.api.factories.IntentRequests;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceNames;
import io.github.serfom256.intentflow.core.pipeline.IntentPipeline;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import io.github.serfom256.intentflow.core.pipeline.factories.IntentPipelines;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.dialect.postgresql.PostgresDialect;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogOverridesTest {

    @IntentService(value = "books", uri = "/books/{{id}}", defaultSort = "id", description = "Books in stock")
    public record Book(@IntentField long id, @IntentField(description = "title as printed") String title,
            @IntentField String isbn) {
    }

    private static final ServiceName BOOKS = ServiceNames.of("books");

    private final CatalogOverrides overrides = ServiceCatalogs.inMemoryOverrides();
    private final ServiceCatalog catalog = ServiceCatalogs.withOverrides(ServiceCatalogs.fromAnnotated(Book.class),
            overrides);
    private final List<String> promptedDescriptions = new ArrayList<>();

    private IntentPipeline pipeline() {
        QueryExecutor executor = new QueryExecutor() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
                return (List<T>) List.of(new Book(7, "Dune", "978-0441013593"));
            }
        };
        return IntentPipelines.builder()
                .llm((text, scope) -> {
                    promptedDescriptions.add(scope.get(BOOKS).fields().get(1).description());
                    return DslSources.of("books | take 1");
                })
                .sqlBuilder(SqlBuilders.create(new PostgresDialect()))
                .executor(executor)
                .catalog(catalog)
                .build();
    }

    private static String description(ServiceDescriptor service, String field) {
        return service.fields().stream().filter(f -> f.logical().value().equals(field)).findFirst().orElseThrow()
                .description();
    }

    @Test
    void overridesReplaceDeclaredValuesAndRemovingRestoresThem() {
        overrides.put(BOOKS, new ServiceOverride("Every book we sell", "/catalog/{{isbn}}",
                Map.of("title", "full title including subtitle")));

        ServiceDescriptor books = catalog.get(BOOKS);
        assertThat(books.description().value()).isEqualTo("Every book we sell");
        assertThat(books.uriTemplate().value()).isEqualTo("/catalog/{{isbn}}");
        assertThat(description(books, "title")).isEqualTo("full title including subtitle");
        assertThat(description(books, "isbn")).isNull();

        overrides.remove(BOOKS);
        books = catalog.get(BOOKS);
        assertThat(books.description().value()).isEqualTo("Books in stock");
        assertThat(books.uriTemplate().value()).isEqualTo("/books/{{id}}");
        assertThat(description(books, "title")).isEqualTo("title as printed");
    }

    @Test
    void anEditReachesTheLlmAndTheUriWithoutWaitingForTheCache() {
        IntentPipeline pipeline = pipeline();
        assertThat(pipeline.resolve(IntentRequests.of("a book")).uri().value()).isEqualTo("/books/7");
        assertThat(pipeline.resolve(IntentRequests.of("a book")).trace().cached()).isTrue();

        overrides.put(BOOKS, new ServiceOverride(null, "/catalog/{{isbn}}", Map.of("title", "the full title")));

        IntentResponse<?> response = pipeline.resolve(IntentRequests.of("a book"));
        assertThat(response.trace().cached()).isFalse();
        assertThat(response.uri().value()).isEqualTo("/catalog/978-0441013593");
        assertThat(promptedDescriptions).containsExactly("title as printed", "the full title");
    }

    @Test
    void blankValuesMeanNoOverride() {
        ServiceOverride override = new ServiceOverride("  ", "", Map.of("title", " "));

        assertThat(override.isEmpty()).isTrue();
        overrides.put(BOOKS, override);
        assertThat(overrides.all()).isEmpty();
    }

    @Test
    void reportsEveryProblemWithAnOverride() {
        ServiceOverride bad = new ServiceOverride("x".repeat(1001), "books/{{author}}",
                Map.of("author", "who wrote it", "title", "line one\nline two"));

        assertThat(bad.problems(catalog.get(BOOKS))).containsExactlyInAnyOrder(
                "description is longer than 1000 characters",
                "'author' is not a queryable field of books",
                "description of 'title' must be a single line without control characters",
                "uri must start with '/' or be an absolute URL",
                "uri placeholder {{author}} is not a field of books");
        assertThat(
                new ServiceOverride(null, "https://shop.example/books/{{id}}", Map.of()).problems(catalog.get(BOOKS)))
                .isEmpty();
    }
}
