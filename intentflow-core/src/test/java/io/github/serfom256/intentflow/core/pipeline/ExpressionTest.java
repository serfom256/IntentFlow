package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ast.AstQuery;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.CastExpr;
import io.github.serfom256.intentflow.core.ast.CastType;
import io.github.serfom256.intentflow.core.ast.ExprCompare;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceDescriptors;
import io.github.serfom256.intentflow.core.pipeline.factories.AstValidators;
import io.github.serfom256.intentflow.core.pipeline.factories.DslParsers;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.core.schema.SchemaFacts;
import io.github.serfom256.intentflow.core.sql.Optimization;
import io.github.serfom256.intentflow.core.testing.TestDialect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpressionTest {

    private static final Set<Optimization> FUNCTIONS_AS_WRITTEN = EnumSet
            .complementOf(EnumSet.of(Optimization.DATE_RANGES));

    @IntentService(value = "orders", uri = "/orders/{{id}}", defaultSort = "id")
    record Order(@IntentField long id, @IntentField String code, @IntentField BigDecimal total,
            @IntentField Integer discount, @IntentField LocalDateTime createdAt, @IntentField boolean paid) {
    }

    private static final ServiceCatalog CATALOG = ServiceCatalogs.of(ServiceDescriptors.fromAnnotated(Order.class));

    private static AstQuery parse(String dsl) {
        return DslParsers.create().parse(DslSources.of(dsl));
    }

    private static String rendered(String dsl) {
        return SqlBuilders.create(new TestDialect(), SchemaFacts.none(), FUNCTIONS_AS_WRITTEN)
                .build(AstValidators.create().validate(parse(dsl), CATALOG))
                .sql();
    }

    private static String declared(String dsl) {
        SchemaFacts facts = SchemaFacts.of(Map.of("order", ServiceDescriptors.factsOf(Order.class, List.of())),
                Map.of());
        return SqlBuilders.create(new TestDialect(), facts, FUNCTIONS_AS_WRITTEN)
                .build(AstValidators.create().validate(parse(dsl), CATALOG)).sql();
    }

    @Test
    void postgresStyleCastsParseLikeCastCalls() {
        assertThat(parse("orders | filter code::bigint > 1000").filter())
                .isEqualTo(parse("orders | filter cast(code as integer) > 1000").filter());
        assertThat(((ExprCompare) parse("orders | filter \"1234\"::bigint == total").filter()).left())
                .isInstanceOf(CastExpr.class)
                .extracting(e -> ((CastExpr) e).type()).isEqualTo(CastType.INTEGER);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "coalesce(discount, 0) > 10 | COALESCE(CAST(\"discount\" AS NUMERIC), ?) > ?",
            "total / discount > 2 | (CAST(\"total\" AS NUMERIC) / NULLIF(\"discount\", 0)) > ?",
            "(total - discount) * 2 >= 100 | ((\"total\" - \"discount\") * ?) >= ?",
            "year(created_at) == 2025 | EXTRACT(YEAR FROM \"created_at\") = ?",
            "month(created_at) in (1, 2) | EXTRACT(MONTH FROM \"created_at\") IN (?, ?)",
            "date_trunc(\"month\", created_at) == \"2026-10-01\" | (DATE_TRUNC('month', \"created_at\") >= CAST(? AS TIMESTAMP) AND DATE_TRUNC('month', \"created_at\") < CAST(? AS TIMESTAMP))",
            "created_at::date == \"2026-10-10\" | CAST(\"created_at\" AS DATE) = CAST(CAST(? AS TIMESTAMP) AS DATE)",
            "code::bigint > 1000 | CAST(\"code\" AS BIGINT) > ?",
            "length(code) > 3 | CHAR_LENGTH(\"code\") > ?",
            "round(total, 1) == 9.5 | ROUND(CAST(\"total\" AS NUMERIC), 1) = ?",
            "coalesce(code, \"none\") == \"A1\" | LOWER(COALESCE(\"code\", ?)) = LOWER(?)",
            "lower(code) ~ \"x\" | LOWER(LOWER(\"code\")) LIKE LOWER(?) ESCAPE '!'",
            "coalesce(discount, total) == null | COALESCE(\"discount\", \"total\") IS NULL",
            "total > discount | \"total\" > \"discount\"",
            "coalesce(created_at, \"2020-01-01T00:00:00\") < \"2021-01-01T00:00:00\" | COALESCE(\"created_at\", CAST(? AS TIMESTAMP)) < CAST(? AS TIMESTAMP)",
    })
    void expressionsRender(String filter, String where) {
        assertThat(rendered("orders | filter " + filter)).isEqualTo(
                "SELECT * FROM \"order\" WHERE " + where + " ORDER BY \"id\" ASC LIMIT 50");
    }

    @Test
    void groupingAndSortingByExpressionsUseAliasesAndExpressions() {
        assertThat(
                rendered("orders | group year(created_at) | sum total * coalesce(discount, 1) | sort year(created_at)"))
                .isEqualTo("SELECT EXTRACT(YEAR FROM \"created_at\") AS \"intentflow_g0\", "
                        + "SUM((\"total\" * COALESCE(CAST(\"discount\" AS NUMERIC), ?))) FROM \"order\" GROUP BY \"intentflow_g0\" "
                        + "ORDER BY \"intentflow_g0\" ASC LIMIT 50");
        assertThat(rendered("orders | sort coalesce(discount, 0) desc"))
                .endsWith("ORDER BY COALESCE(CAST(\"discount\" AS NUMERIC), ?) DESC NULLS LAST, \"id\" ASC LIMIT 50");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "year(code) == 2025 | year needs a timestamp",
            "code + 1 > 2 | arithmetic needs a number",
            "paid::text == \"true\" | cannot cast boolean to text",
            "coalesce(code, 1) == 1 | coalesce mixes text and number",
            "date_trunc(\"week\", created_at) == \"2026-01-01\" | date_trunc takes",
            "round(total, 9) > 1 | round takes a whole number of digits",
            "coalesce(total) > 1 | coalesce takes at least 2",
            "length(code) ~ \"x\" | '~' needs text",
            "total * 2 == \"x\" | cannot compare number with text",
            "secret(total) > 1 | unknown function 'secret'",
    })
    void invalidExpressionsAreRejectedWithAReason(String filter, String message) {
        assertThatThrownBy(() -> rendered("orders | filter " + filter)).hasMessageContaining(message);
    }

    @Test
    void anIntegerColumnIsNeverInAListOfFractions() {
        assertThat(declared("orders | filter id in (1, 2.5)")).contains("WHERE \"id\" IN (?)");
        assertThat(declared("orders | filter id in (2.5)")).contains("WHERE \"id\" <> \"id\"");
        assertThat(declared("orders | filter total in (1, 2.5)")).contains("WHERE \"total\" IN (?, ?)");
    }

    @Test
    void repeatedConditionsAreWrittenOnce() {
        assertThat(rendered("orders | filter discount == null and discount == null"))
                .contains("WHERE \"discount\" IS NULL ORDER BY");
    }
}
