package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentRelation;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.pipeline.factories.AstValidators;
import io.github.serfom256.intentflow.core.pipeline.factories.DslParsers;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.core.testing.TestDialect;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LatestRelatedRowTest {

    @IntentService(value = "customers", table = "customer", uri = "/customers/{{id}}", defaultSort = "id")
    @IntentRelation(name = "orders", service = "orders", from = "id", to = "customer_id")
    @IntentRelation(name = "notes", service = "notes", from = "id", to = "customer_id")
    record Customer(@IntentField long id, @IntentField String country) {
    }

    @IntentService(value = "orders", table = "orders", uri = "/orders/{{id}}", defaultSort = { "created_at desc",
            "id desc" })
    @IntentRelation(name = "customer", service = "customers", from = "customer_id", to = "id")
    record Order(@IntentField long id, @IntentField long customerId, @IntentField String status,
            @IntentField BigDecimal total, @IntentField Instant createdAt) {
    }

    @IntentService(value = "notes", table = "note", uri = "/notes/{{id}}", defaultSort = "id")
    record Note(@IntentField long id, @IntentField long customerId, @IntentField String text) {
    }

    private static final ServiceCatalog CATALOG = ServiceCatalogs.fromAnnotated(Customer.class, Order.class,
            Note.class);

    private static ValidatedQuery validate(String dsl) {
        return AstValidators.create().validate(DslParsers.create().parse(DslSources.of(dsl)), CATALOG);
    }

    private static String sql(String dsl) {
        return SqlBuilders.create(new TestDialect()).build(validate(dsl)).sql();
    }

    @Test
    void latestTestsTheNewestRelatedRowOnly() {
        assertThat(sql("customers | filter orders.latest.status == \"CANCELLED\"")).isEqualTo("SELECT * FROM "
                + "\"customer\" WHERE EXISTS (SELECT 1 FROM \"orders\" \"intentflow_r\" WHERE \"intentflow_r\""
                + ".\"customer_id\" = \"customer\".\"id\" AND LOWER(\"intentflow_r\".\"status\") = LOWER(?) AND NOT "
                + "(EXISTS (SELECT 1 FROM \"orders\" \"intentflow_p\" WHERE \"intentflow_p\".\"customer_id\" = "
                + "\"customer\".\"id\" AND (\"intentflow_p\".\"created_at\" > \"intentflow_r\".\"created_at\" OR "
                + "(\"intentflow_p\".\"created_at\" = \"intentflow_r\".\"created_at\" AND \"intentflow_p\".\"id\" > "
                + "\"intentflow_r\".\"id\"))))) ORDER BY \"id\" ASC LIMIT 50");
    }

    @Test
    void earliestReversesTheOrder() {
        assertThat(sql("customers | filter orders.earliest.total > 100"))
                .contains("\"intentflow_p\".\"created_at\" < \"intentflow_r\".\"created_at\"")
                .contains("\"intentflow_p\".\"id\" < \"intentflow_r\".\"id\"");
    }

    @Test
    void latestConditionsShareOneRowAndStaySeparateFromAnyRowConditions() {
        String sql = sql("customers | filter orders.latest.status == \"PAID\" and orders.latest.total > 100 "
                + "and orders.total > 1000");

        assertThat(sql.split("EXISTS \\(SELECT 1 FROM \"orders\" \"intentflow_r\"", -1)).hasSize(3);
        assertThat(sql).containsOnlyOnce("NOT (EXISTS");
    }

    @Test
    void latestIsOnlyForFilters() {
        assertThatThrownBy(() -> validate("customers | sort orders.latest.total desc")).isInstanceOfSatisfying(
                ValidationException.class,
                e -> assertThat(e.diagnostic().orElseThrow().render("customers | sort orders.latest.total desc"))
                        .contains("only works in filter"));
    }

    @Test
    void latestNeedsADateOnTheRelatedService() {
        assertThatThrownBy(() -> validate("customers | filter notes.latest.text ~ \"call\""))
                .isInstanceOf(ValidationException.class).hasMessageContaining("needs a date that orders 'notes'");
    }

    @Test
    void anUnknownFieldBehindLatestSuggestsTheRelatedFields() {
        assertThatThrownBy(() -> validate("customers | filter orders.latest.state == \"PAID\"")).isInstanceOfSatisfying(
                ValidationException.class,
                e -> assertThat(
                        e.diagnostic().orElseThrow().render("customers | filter orders.latest.state == \"PAID\""))
                        .contains("did you mean 'orders.latest.status'?"));
    }
}
