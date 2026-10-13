package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.api.factories.IntentRequests;
import io.github.serfom256.intentflow.core.ast.AggregateFunction;
import io.github.serfom256.intentflow.core.ast.AstQuery;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentRelation;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceDescriptors;
import io.github.serfom256.intentflow.core.pipeline.factories.AstValidators;
import io.github.serfom256.intentflow.core.pipeline.factories.DslCaches;
import io.github.serfom256.intentflow.core.pipeline.factories.DslParsers;
import io.github.serfom256.intentflow.core.pipeline.factories.IntentPipelines;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.core.pipeline.factories.TextNormalizers;
import io.github.serfom256.intentflow.core.pipeline.factories.UriBuilders;
import io.github.serfom256.intentflow.dialect.postgresql.PostgresDialect;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RelationsAndAggregatesTest {

    @IntentService(value = "customers", uri = "/customers/{{id}}", defaultSort = "id")
    @IntentRelation(name = "orders", service = "orders", from = "id", to = "customer_id")
    record Customer(@IntentField long id, @IntentField String country, @IntentField(caseSensitive = true) String code) {
    }

    @IntentService(value = "orders", table = "orders", uri = "/orders/{{id}}", defaultSort = "id")
    @IntentRelation(name = "customer", service = "customers", from = "customer_id", to = "id")
    record Order(@IntentField long id, @IntentField long customerId, @IntentField String status,
                 @IntentField BigDecimal total) {
    }

    private static final ServiceCatalog CATALOG = ServiceCatalogs.of(
            ServiceDescriptors.fromAnnotated(Customer.class), ServiceDescriptors.fromAnnotated(Order.class));

    private static BoundQuery sql(String dsl) {
        AstQuery ast = DslParsers.create().parse(DslSources.of(dsl));
        return SqlBuilders.create(new PostgresDialect()).build(AstValidators.create().validate(ast, CATALOG));
    }

    private static AstQuery parse(String dsl) {
        return DslParsers.create().parse(DslSources.of(dsl));
    }

    @Test
    void parsesAggregateStagesAndDottedFields() {
        AstQuery q = parse("orders | filter customer.country == \"DE\" | group status | sum total | sort sum desc");

        assertThat(q.aggregate().function()).isEqualTo(AggregateFunction.SUM);
        assertThat(q.aggregate().field().value()).isEqualTo("total");
        assertThat(q.aggregate().groupBy()).extracting(f -> f.value()).containsExactly("status");
        assertThat(q.sort().getFirst().field().value()).isEqualTo("sum");
    }

    @Test
    void aggregateWordsAreStillFieldNamesInFilters() {
        assertThat(parse("stats | filter count > 3 and min == 1").aggregate()).isNull();
    }

    @Test
    void groupNeedsAFunctionAndCountTakesNoField() {
        assertThatThrownBy(() -> parse("orders | group status")).isInstanceOf(DslParseException.class)
                .hasMessageContaining("needs an aggregate stage");
        assertThatThrownBy(() -> parse("orders | count | sum total")).isInstanceOf(DslParseException.class)
                .hasMessageContaining("only one aggregate stage");
        assertThatThrownBy(() -> parse("orders | sum")).isInstanceOf(DslParseException.class);
    }

    @Test
    void relationConditionsBecomeOneExistsPerRelationInAnAnd() {
        BoundQuery q = sql("customers | filter orders.total > 100 and orders.status == \"PAID\" and country == \"DE\"");

        assertThat(q.sql()).isEqualTo("SELECT * FROM \"customer\" WHERE (EXISTS (SELECT 1 FROM \"orders\" "
                + "\"intentflow_r\" WHERE \"intentflow_r\".\"customer_id\" = \"customer\".\"id\" "
                + "AND \"intentflow_r\".\"total\" > ? AND LOWER(\"intentflow_r\".\"status\") = LOWER(?)) "
                + "AND LOWER(\"country\") = LOWER(?)) ORDER BY \"id\" ASC LIMIT 50");
    }

    @Test
    void notAroundARelationIsNotExists() {
        assertThat(sql("customers | filter not (orders.status == \"CANCELLED\")").sql())
                .startsWith("SELECT * FROM \"customer\" WHERE NOT (EXISTS (SELECT 1 FROM \"orders\"");
    }

    @Test
    void caseSensitiveFieldsAreComparedWithoutLower() {
        assertThat(sql("customers | filter code == \"Ab\" and code in (\"x\", \"Y\")").sql())
                .contains("\"code\" = ?").contains("\"code\" IN (?, ?)").doesNotContain("LOWER(\"code\")");
    }

    @Test
    void sortsGetTheDefaultSortAsTieBreaker() {
        assertThat(sql("orders | sort status desc").sql()).endsWith("ORDER BY \"status\" DESC NULLS LAST, \"id\" ASC LIMIT 50");
        assertThat(sql("orders | sort id desc").sql()).endsWith("ORDER BY \"id\" DESC NULLS LAST LIMIT 50");
    }

    @Test
    void aggregatesGroupAndSortByValueThenGroups() {
        assertThat(sql("orders | group status, customer_id | count").sql()).isEqualTo(
                "SELECT \"status\", \"customer_id\", COUNT(*) FROM \"orders\" GROUP BY \"status\", \"customer_id\" "
                        + "ORDER BY COUNT(*) DESC NULLS LAST, \"status\" ASC, \"customer_id\" ASC LIMIT 50");
        assertThat(sql("orders | filter status == \"PAID\" | avg total").sql()).isEqualTo(
                "SELECT AVG(\"total\") FROM \"orders\" WHERE LOWER(\"status\") = LOWER(?) LIMIT 50");
        assertThat(sql("orders | group status | max total | sort status").sql())
                .contains("ORDER BY \"status\" ASC LIMIT 50");
    }

    @Test
    void validatorRejectsWrongAggregatesAndRelations() {
        assertThatThrownBy(() -> sql("orders | sum status")).isInstanceOf(ValidationException.class)
                .hasMessageContaining("sum needs a number field");
        assertThatThrownBy(() -> sql("orders | group status | count | sort total")).isInstanceOf(ValidationException.class)
                .hasMessageContaining("sort by 'count' or a group field");
        assertThatThrownBy(() -> sql("orders | group customer.country | count")).isInstanceOf(ValidationException.class)
                .hasMessageContaining("related field");
        assertThatThrownBy(() -> sql("orders | filter buyer.country == \"DE\"")).isInstanceOf(ValidationException.class)
                .hasMessageContaining("'buyer' is not a relation of 'orders'");
        assertThatThrownBy(() -> sql("orders | filter customer.missing == 1")).isInstanceOf(ValidationException.class)
                .hasMessageContaining("field 'customer.missing' is not declared");
    }

    @Test
    void scopedResolveLetsTheLlmSeeRelatedServicesButAnswersOnlyTheScopedOne() {
        List<String> seen = new ArrayList<>();
        QueryExecutor executor = new QueryExecutor() {
            @Override
            public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
                return List.of();
            }

            @Override
            public List<List<@Nullable Object>> rows(BoundQuery query) {
                return List.of(List.of("PAID", 3L));
            }
        };
        IntentPipeline pipeline = IntentPipelines.create(TextNormalizers.create(), DslCaches.create(),
                (text, catalog) -> {
                    seen.addAll(catalog.names().stream().map(n -> n.value()).sorted().toList());
                    return DslSources.of(text.value().equals("count") ? "orders | group status | count" : "customers");
                },
                DslParsers.create(), AstValidators.create(), SqlBuilders.create(new PostgresDialect()), executor,
                UriBuilders.create(), CATALOG);

        var response = pipeline.resolve(IntentRequests.of("count"), Order.class);
        assertThat(seen).containsExactly("customers", "orders");
        assertThat(response.aggregation().rows().getFirst().value()).isEqualTo(BigDecimal.valueOf(3));
        assertThat(response.trace().dsl()).isEqualTo("orders | group status | count");
        assertThat(response.trace().cached()).isFalse();
        assertThat(pipeline.resolve(IntentRequests.of("count"), Order.class).trace().cached()).isTrue();

        assertThatThrownBy(() -> pipeline.resolve(IntentRequests.of("other"), Order.class))
                .isInstanceOf(io.github.serfom256.intentflow.core.catalog.ServiceNotFoundException.class);
    }
}
