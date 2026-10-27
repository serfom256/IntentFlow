package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ast.factories.AstQueries;
import io.github.serfom256.intentflow.core.catalog.FieldMapping;
import io.github.serfom256.intentflow.core.catalog.FieldType;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.catalog.factories.ColumnNames;
import io.github.serfom256.intentflow.core.catalog.factories.FieldMappings;
import io.github.serfom256.intentflow.core.catalog.factories.LogicalFieldNames;
import io.github.serfom256.intentflow.core.catalog.factories.MaxTakes;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceDescriptors;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceNames;
import io.github.serfom256.intentflow.core.catalog.factories.TableNames;
import io.github.serfom256.intentflow.core.catalog.factories.UriTemplates;
import io.github.serfom256.intentflow.core.catalog.impl.DefaultFieldMapping;
import io.github.serfom256.intentflow.core.pipeline.factories.UriBuilders;
import io.github.serfom256.intentflow.core.pipeline.factories.ValidatedQueries;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UriBuilderTest {

    private final UriBuilder uriBuilder = UriBuilders.create();

    private static final ServiceName BOOKS = ServiceNames.of("books");

    enum Genre {
        SCI_FI
    }

    record Book(long id, String title, String authorName, BigDecimal price, boolean inStock, Instant publishedAt,
            Genre genre, String isbn) {
    }

    static class BeanBook {
        private final long id;

        BeanBook(long id) {
            this.id = id;
        }

        public long getId() {
            return id;
        }

        public boolean isFeatured() {
            return true;
        }
    }

    static class BaseEntity {
        protected Long id = 99L;
    }

    static class FieldBook extends BaseEntity {
        String title = "Dune";
    }

    private static FieldMapping field(String logical, String property) {
        return new DefaultFieldMapping(LogicalFieldNames.of(logical), ColumnNames.of(logical), true, FieldType.ANY,
                null, List.of(), property);
    }

    private static ValidatedQuery query(String template, Class<?> type, FieldMapping... fields) {
        ServiceDescriptor svc = ServiceDescriptors.of(BOOKS, null, TableNames.of("book"), List.of(fields), List.of(),
                MaxTakes.of(50), UriTemplates.of(template), type);
        return ValidatedQueries.of(svc, AstQueries.of(BOOKS, null, List.of(), null, null));
    }

    private static Book book(long id) {
        return new Book(id, "Dune", "Frank Herbert", new BigDecimal("9.50"), true,
                Instant.parse("2026-01-31T10:15:30Z"), Genre.SCI_FI, "978/0441");
    }

    @Test
    void readsRecordComponentsOfTheFirstItem() {
        ValidatedQuery q = query("/books/{{id}}", Book.class, field("id", "id"));

        assertThat(uriBuilder.build(q, List.of(book(42), book(7))).value()).isEqualTo("/books/42");
    }

    @Test
    void propertyDefaultsToCamelCaseOfLogicalName() {
        FieldMapping author = FieldMappings.of(LogicalFieldNames.of("author_name"), ColumnNames.of("author"), true);
        ValidatedQuery q = query("/authors/{{author_name}}", Book.class, author);

        assertThat(uriBuilder.build(q, List.of(book(1))).value()).isEqualTo("/authors/Frank%20Herbert");
    }

    @Test
    void readsGettersAndBooleanIsGetters() {
        ValidatedQuery q = query("/books/{{id}}?featured={{featured}}", BeanBook.class, field("id", "id"),
                field("featured", "featured"));

        assertThat(uriBuilder.build(q, List.of(new BeanBook(5))).value()).isEqualTo("/books/5?featured=true");
    }

    @Test
    void readsInheritedNonPublicFields() {
        ValidatedQuery q = query("/books/{{id}}/{{title}}", FieldBook.class, field("id", "id"),
                field("title", "title"));

        assertThat(uriBuilder.build(q, List.of(new FieldBook())).value()).isEqualTo("/books/99/Dune");
    }

    @Test
    void rendersValuesByType() {
        ValidatedQuery q = query("/x/{{price}}/{{in_stock}}/{{published_at}}/{{genre}}", Book.class,
                field("price", "price"), field("in_stock", "inStock"), field("published_at", "publishedAt"),
                field("genre", "genre"));

        assertThat(uriBuilder.build(q, List.of(book(1))).value())
                .isEqualTo("/x/9.50/true/2026-01-31T10%3A15%3A30Z/SCI_FI");
    }

    @Test
    void encodesReservedCharactersInValues() {
        ValidatedQuery q = query("/isbn/{{isbn}}", Book.class, field("isbn", "isbn"));

        assertThat(uriBuilder.build(q, List.of(book(1))).value()).isEqualTo("/isbn/978%2F0441");
    }

    @Test
    void nullValueRendersEmpty() {
        ValidatedQuery q = query("/books/{{title}}", Book.class, field("title", "title"));
        Book untitled = new Book(1, null, null, null, false, null, null, null);

        assertThat(uriBuilder.build(q, List.of(untitled)).value()).isEqualTo("/books/");
    }

    @Test
    void repeatedPlaceholderResolvesEachOccurrence() {
        ValidatedQuery q = query("/{{id}}/{{id}}", Book.class, field("id", "id"));

        assertThat(uriBuilder.build(q, List.of(book(3))).value()).isEqualTo("/3/3");
    }

    @Test
    void emptyResultDropsPlaceholderPartsOfTemplate() {
        assertThat(uriBuilder.build(query("/books/{{id}}", Book.class, field("id", "id")), List.of()).value())
                .isEqualTo("/books");
        assertThat(uriBuilder.build(
                query("/authors/{{author}}/books/{{id}}", Book.class, field("id", "id"), field("author", "authorName")),
                List.of()).value()).isEqualTo("/authors");
        assertThat(
                uriBuilder.build(query("/books?id={{id}}&view=list", Book.class, field("id", "id")), List.of()).value())
                .isEqualTo("/books?view=list");
        assertThat(uriBuilder.build(query("/{{id}}", Book.class, field("id", "id")), List.of()).value()).isEqualTo("/");
        assertThat(uriBuilder.build(query("/books", Book.class, field("id", "id")), List.of()).value())
                .isEqualTo("/books");
    }

    @Test
    void unknownPlaceholderIsRejectedEvenWhenEmpty() {
        ValidatedQuery q = query("/books/{{nope}}", Book.class, field("id", "id"));

        assertThatThrownBy(() -> uriBuilder.build(q, List.of(book(1)))).isInstanceOf(UriBuildException.class)
                .hasMessageContaining("nope");
        assertThatThrownBy(() -> uriBuilder.build(q, List.of())).isInstanceOf(UriBuildException.class)
                .hasMessageContaining("nope");
    }

    @Test
    void missingPropertyOnEntityIsReported() {
        ValidatedQuery q = query("/books/{{sku}}", Book.class, field("sku", "sku"));

        assertThatThrownBy(() -> uriBuilder.build(q, List.of(book(1)))).isInstanceOf(UriBuildException.class)
                .hasMessageContaining("no property 'sku'").hasMessageContaining("{{sku}}");
    }
}
