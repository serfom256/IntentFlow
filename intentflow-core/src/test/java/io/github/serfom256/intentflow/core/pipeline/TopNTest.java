package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ast.AstQuery;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.Expressions;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentRelation;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceDescriptors;
import io.github.serfom256.intentflow.core.pipeline.factories.AstValidators;
import io.github.serfom256.intentflow.core.pipeline.factories.DslParsers;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.core.testing.TestDialect;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class TopNTest {

    @IntentService(value = "customers", uri = "/customers/{{id}}", defaultSort = "id")
    record Customer(@IntentField long id, @IntentField String country) {
    }

    @IntentService(value = "orders", table = "orders", uri = "/orders/{{id}}", defaultSort = "id", maxTake = 100)
    @IntentRelation(name = "customer", service = "customers", from = "customer_id", to = "id")
    record Order(@IntentField long id, @IntentField long customerId, @IntentField String status,
            @IntentField BigDecimal total, @IntentField Instant createdAt) {
    }

    private static final ServiceCatalog CATALOG = ServiceCatalogs.of(ServiceDescriptors.fromAnnotated(Customer.class),
            ServiceDescriptors.fromAnnotated(Order.class));

    private static AstQuery parse(String dsl) {
        return DslParsers.create().parse(DslSources.of(dsl));
    }

    private static ValidatedQuery validate(String dsl) {
        return AstValidators.create().validate(parse(dsl), CATALOG);
    }

    private static BoundQuery sql(String dsl) {
        return SqlBuilders.create(new TestDialect()).build(validate(dsl));
    }

    @Test
    void parsesCountPartitionAndOrder() {
        AstQuery q = parse("orders | top 3 per customer_id, year(created_at) by total desc, id");

        assertThat(q.top().orElseThrow().count()).isEqualTo(3);
        assertThat(q.top().orElseThrow().partition()).extracting(Expressions::print).containsExactly("customer_id",
                "year(created_at)");
        assertThat(q.top().orElseThrow().order()).extracting(k -> k.field().value(), SortKey::direction)
                .containsExactly(tuple("total", SortKey.Direction.DESC), tuple("id", SortKey.Direction.ASC));
    }

    @Test
    void partitionIsOptional() {
        assertThat(parse("orders | top 5 by total desc").top().orElseThrow().partition()).isEmpty();
    }

    @Test
    void rejectsMalformedTop() {
        assertThatThrownBy(() -> parse("orders | top 3 per customer_id")).isInstanceOf(DslParseException.class)
                .hasMessageContaining("expected 'by'");
        assertThatThrownBy(() -> parse("orders | top 0 by total")).isInstanceOf(DslParseException.class)
                .hasMessageContaining("must be positive");
        assertThatThrownBy(() -> parse("orders | top 1 by total | top 2 by id")).isInstanceOf(DslParseException.class)
                .hasMessageContaining("duplicate 'top'");
    }

    @Test
    void windowOrderEndsWithTheDefaultSortSoTiesAreStable() {
        assertThat(validate("orders | top 1 per customer_id by total desc").topOrder())
                .extracting(k -> k.field().value()).containsExactly("total", "id");
    }

    @Test
    void resultsWithoutASortKeepTheTopOrder() {
        assertThat(validate("orders | top 3 by total desc").effectiveSort())
                .extracting(k -> k.field().value(), SortKey::direction)
                .containsExactly(tuple("total", SortKey.Direction.DESC), tuple("id", SortKey.Direction.ASC));
    }

    @Test
    void rendersRowNumberInASubqueryNamedAfterTheTable() {
        BoundQuery q = sql(
                "orders | filter status == \"PAID\" | top 2 per customer_id by total desc | sort total desc");

        assertThat(q.sql()).isEqualTo("SELECT * FROM (SELECT \"orders\".*, ROW_NUMBER() OVER (PARTITION BY "
                + "\"customer_id\" ORDER BY \"total\" DESC NULLS LAST, \"id\" ASC) AS \"intentflow_rank\" "
                + "FROM \"orders\" WHERE LOWER(\"status\") = LOWER(?)) \"orders\" WHERE \"intentflow_rank\" <= 2 "
                + "ORDER BY \"total\" DESC NULLS LAST, \"id\" ASC LIMIT 100");
    }

    @Test
    void rejectsTopWithAggregatesRelatedFieldsOrTooManyRows() {
        assertThatThrownBy(() -> validate("orders | top 1 by total | count")).isInstanceOf(ValidationException.class)
                .hasMessageContaining("cannot be combined");
        assertThatThrownBy(() -> validate("orders | top 1 per customer.country by total"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> validate("orders | top 1000 by total")).isInstanceOf(ValidationException.class)
                .hasMessageContaining("exceeds maxTake");
    }
}
