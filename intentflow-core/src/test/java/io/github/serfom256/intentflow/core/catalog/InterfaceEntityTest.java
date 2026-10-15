package io.github.serfom256.intentflow.core.catalog;

import io.github.serfom256.intentflow.core.ast.factories.AstQueries;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceDescriptors;
import io.github.serfom256.intentflow.core.pipeline.factories.UriBuilders;
import io.github.serfom256.intentflow.core.pipeline.factories.ValidatedQueries;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class InterfaceEntityTest {

    interface Named {
        @IntentField
        String name();
    }

    @IntentService(value = "books", uri = "/books/{{id}}", defaultSort = "id")
    interface Book extends Named {

        @IntentField
        long id();

        @IntentField(description = "USD")
        BigDecimal getPrice();

        @IntentField
        boolean isInStock();

        String internalNote();
    }

    record BookImpl(long id, String name, BigDecimal price, boolean inStock) implements Book {
        @Override
        public BigDecimal getPrice() {
            return price;
        }

        @Override
        public boolean isInStock() {
            return inStock;
        }

        @Override
        public String internalNote() {
            return "hidden";
        }
    }

    @Test
    void propertyMethodsOfInterfacesAndTheirParentsBecomeFields() {
        ServiceDescriptor books = ServiceDescriptors.fromAnnotated(Book.class);

        assertThat(books.fields())
                .extracting(f -> f.logical().value(), f -> f.column().value(), FieldMapping::type, FieldMapping::property)
                .containsExactly(
                        tuple("name", "name", FieldType.TEXT, "name"),
                        tuple("id", "id", FieldType.NUMBER, "id"),
                        tuple("in_stock", "in_stock", FieldType.BOOLEAN, "inStock"),
                        tuple("price", "price", FieldType.NUMBER, "price"));
    }

    @Test
    void nameResolversSeeTheMethod() {
        NameResolver resolver = new NameResolver() {
            @Override
            public Optional<String> columnName(Method method) {
                return method.getName().equals("getPrice") ? Optional.of("price_usd") : Optional.empty();
            }
        };

        assertThat(ServiceDescriptors.fromAnnotated(Book.class, List.of(resolver)).fields())
                .filteredOn(f -> f.logical().value().equals("price"))
                .extracting(f -> f.column().value())
                .containsExactly("price_usd");
    }

    @Test
    void uriPlaceholdersReadAccessorMethods() {
        ServiceDescriptor books = ServiceDescriptors.fromAnnotated(Book.class);
        var query = ValidatedQueries.of(books, AstQueries.of(books.name(), null, List.of(), null, null));

        assertThat(UriBuilders.create().build(query, List.of(new BookImpl(7, "Dune", BigDecimal.ONE, true))).value())
                .isEqualTo("/books/7");
    }
}
