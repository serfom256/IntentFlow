package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.api.UserText;
import io.github.serfom256.intentflow.core.api.factories.IntentRequests;
import io.github.serfom256.intentflow.core.ast.And;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.DslSource;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentRelation;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.pipeline.factories.AstValidators;
import io.github.serfom256.intentflow.core.pipeline.factories.DslParsers;
import io.github.serfom256.intentflow.core.pipeline.factories.IntentPipelines;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.core.testing.TestDialect;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiagnosticTest {

    @IntentService(value = "customers", table = "customer", uri = "/customers/{{id}}", defaultSort = "id")
    @IntentRelation(name = "orders", service = "orders", from = "id", to = "customer_id")
    record Customer(@IntentField long id, @IntentField String country, @IntentField boolean vip) {
    }

    @IntentService(value = "orders", table = "orders", uri = "/orders/{{id}}", defaultSort = "id", maxTake = 50)
    @IntentRelation(name = "customer", service = "customers", from = "customer_id", to = "id")
    record Order(@IntentField long id, @IntentField long customerId, @IntentField String status,
            @IntentField BigDecimal total, @IntentField Instant createdAt) {
    }

    private static final ServiceCatalog CATALOG = ServiceCatalogs.fromAnnotated(Customer.class, Order.class);

    private static String rendered(String dsl) {
        try {
            AstValidators.create().validate(DslParsers.create().parse(DslSources.of(dsl)), CATALOG);
        } catch (DslParseException e) {
            return e.diagnostic().orElseThrow().render(dsl);
        } catch (ValidationException e) {
            return e.diagnostic().orElseThrow().render(dsl);
        }
        throw new AssertionError("accepted: " + dsl);
    }

    @Test
    void aConditionInTopPointsAtItAndSuggestsTheFilter() {
        assertThat(rendered("customers | filter country == \"FR\" | top 1 per id by orders.status == \"NEW\""))
                .isEqualTo(
                        """
                                error: 'top ... by' ranks rows by fields, not by conditions
                                  |
                                1 | customers | filter country == "FR" | top 1 per id by orders.status == "NEW"
                                  |                                                      ^^^^^^^^^^^^^^^^^^^^^^ a condition
                                  = note: a condition on a related field, like orders.status == "NEW", keeps the rows that have at \
                                least one matching related row
                                  = help: move the condition into the filter: filter orders.status == "NEW\"""");
    }

    @Test
    void aSecondByInTopSaysToUseCommas() {
        assertThat(rendered("orders | top 1 per customer_id by created_at desc by id")).isEqualTo("""
                error: 'top' takes one 'by' list
                  |
                1 | orders | top 1 per customer_id by created_at desc by id
                  |                                                   ^^ second 'by'
                  = help: separate the order keys with commas: top 3 per category by rating desc, price""");
    }

    @Test
    void anUnknownFieldSuggestsTheClosestOne() {
        assertThat(rendered("orders | filter totl > 100")).isEqualTo("""
                error: unknown field 'totl'
                  |
                1 | orders | filter totl > 100
                  |                 ^^^^ not a field here
                  = help: did you mean 'total'? the fields are created_at, customer_id, id, status, total""");
        assertThat(rendered("customers | filter orders.state == \"NEW\"")).contains("^^^^^^^^^^^^")
                .contains("did you mean 'orders.status'?");
    }

    @Test
    void aFunctionOnARelatedFieldSuggestsComparingTheFieldDirectly() {
        assertThat(rendered("customers | filter year(orders.created_at) == 2026"))
                .contains("error: related field 'orders.created_at' cannot be used inside a function or arithmetic")
                .contains("                        ^^^^^^^^^^^^^^^^^ a related field")
                .contains("help: compare the related field directly, without a function: orders.created_at >= "
                        + "\"2026-01-01\" and orders.created_at < \"2027-01-01\" for a year");
    }

    @Test
    void aFilterOnTheAggregateExplainsThatItCannotBeExpressed() {
        assertThat(rendered("orders | group status | count | filter count > 3 and"))
                .contains("error: a filter cannot test the aggregated value").contains("^^^^^^ this filter")
                .contains("like SQL's HAVING");
    }

    @Test
    void unexpectedTextAfterTheQueryListsTheStages() {
        assertThat(rendered("orders | sort total desc limit 5")).contains("^^^^^ not expected here")
                .contains("the stages are filter, group");
    }

    @Test
    void lexerErrorsSuggestTheDslSpelling() {
        assertThat(rendered("orders | filter status = \"NEW\"")).contains("^ not part of the query language")
                .contains("help: compare with '=='");
    }

    @Test
    void anUnknownFunctionListsTheFunctions() {
        assertThat(rendered("orders | filter weekday(created_at) == 6")).contains("^^^^^^^ not a function")
                .contains("the functions are").contains("year").contains("are stages after '|'");
    }

    @Test
    void repeatedFiltersAreCombinedWithAnd() {
        assertThat(DslParsers.create().parse(DslSources.of("orders | filter status == \"NEW\" | filter total > 5"))
                .filter().orElseThrow()).isInstanceOf(And.class);
    }

    @Test
    void theRepairRequestCarriesTheRenderedDiagnostic() {
        List<String> errors = new ArrayList<>();
        LlmClient llm = new LlmClient() {
            @Override
            public DslSource generateDsl(UserText text, ServiceCatalog catalog) {
                return DslSources.of("orders | filter totl > 100");
            }

            @Override
            public DslSource repairDsl(UserText text, ServiceCatalog catalog, DslSource failed, String error) {
                errors.add(error);
                return DslSources.of("orders | filter total > 100");
            }
        };
        QueryExecutor executor = new QueryExecutor() {
            @Override
            public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
                return List.of();
            }
        };

        IntentPipelines.builder().llm(llm).sqlBuilder(SqlBuilders.create(new TestDialect())).executor(executor)
                .catalog(CATALOG).build().resolve(IntentRequests.of("big orders"));

        assertThat(errors).singleElement().asString().startsWith("error: unknown field 'totl'")
                .contains("1 | orders | filter totl > 100").contains("did you mean 'total'?");
    }

    @Test
    void errorsWithoutADiagnosticAreStillParseExceptions() {
        assertThatThrownBy(() -> DslParsers.create().parse(DslSources.of("orders | filter")))
                .isInstanceOf(DslParseException.class);
    }
}
