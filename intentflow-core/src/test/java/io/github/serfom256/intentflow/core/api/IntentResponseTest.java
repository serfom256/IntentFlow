package io.github.serfom256.intentflow.core.api;

import io.github.serfom256.intentflow.core.api.factories.IntentResponses;
import io.github.serfom256.intentflow.core.api.factories.MicroFrontendUris;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceNames;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntentResponseTest {

    record Book(long id) {
    }

    @Test
    void carriesServiceTypeItemsAndUri() {
        IntentResponse<Book> response = IntentResponses.of(ServiceNames.of("books"), Book.class,
                List.of(new Book(1), new Book(2)), MicroFrontendUris.of("/books/1"));

        assertThat(response.service().value()).isEqualTo("books");
        assertThat(response.type()).isEqualTo(Book.class);
        assertThat(response.items()).containsExactly(new Book(1), new Book(2));
        assertThat(response.uri().value()).isEqualTo("/books/1");
    }

    @Test
    void itemsAreCopiedAndImmutable() {
        List<Book> source = new ArrayList<>(List.of(new Book(1)));
        IntentResponse<Book> response = IntentResponses.of(ServiceNames.of("books"), Book.class, source,
                MicroFrontendUris.of("/books/1"));
        source.add(new Book(2));

        assertThat(response.items()).hasSize(1);
        assertThatThrownBy(() -> response.items().add(new Book(3))).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void rejectsItemsOfAnotherType() {
        List raw = List.of("not a book");
        assertThatThrownBy(
                () -> IntentResponses.of(ServiceNames.of("books"), Book.class, raw, MicroFrontendUris.of("/books")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("java.lang.String");
    }
}
