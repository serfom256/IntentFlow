package io.github.serfom256.intentflow.starter.autoconfigure.other;

import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;

@IntentService(value = "magazines", uri = "/magazines/{{id}}")
public record Magazine(@IntentField long id, @IntentField String title) {
}
