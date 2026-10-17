package io.github.serfom256.intentflow.admin;

import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;

@IntentService(value = "books", table = "book", uri = "/books/{{id}}", defaultSort = "id", description = "Books in stock")
public record Book(@IntentField long id, @IntentField(description = "title as printed") String title,
        @IntentField String isbn) {
}
