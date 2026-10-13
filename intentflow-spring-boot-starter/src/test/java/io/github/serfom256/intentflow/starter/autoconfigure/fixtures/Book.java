package io.github.serfom256.intentflow.starter.autoconfigure.fixtures;

import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;

import java.math.BigDecimal;

@IntentService(value = "books", uri = "/books/{{id}}", description = "Library books")
public record Book(@IntentField long id, @IntentField String title, @IntentField BigDecimal price) {

    @IntentService(value = "authors", uri = "/authors/{{id}}")
    public record Author(@IntentField long id, @IntentField String name) {
    }
}
