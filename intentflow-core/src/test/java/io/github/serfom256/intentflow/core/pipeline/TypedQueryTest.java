package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.pipeline.factories.AstValidators;
import io.github.serfom256.intentflow.core.pipeline.factories.DslParsers;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.core.testing.TestDialect;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypedQueryTest {

    private static final ServiceCatalog CATALOG = ServiceCatalogs.of(GeneratedQueryTest.ITEMS);

    private final DslParser parser = DslParsers.create();
    private final AstValidator validator = AstValidators.create();
    private final SqlBuilder sqlBuilder = SqlBuilders.create(new TestDialect());

    private String sql(String filter) {
        ValidatedQuery validated = validator.validate(parser.parse(DslSources.of("items | filter " + filter)), CATALOG);
        String sql = sqlBuilder.build(validated).sql();
        return sql.substring(sql.indexOf(" WHERE ") + 7, sql.indexOf(" ORDER BY "));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "name == \"Dune\"                       | LOWER(\"item_name\") = LOWER(?)",
            "notes != \"x\"                         | (LOWER(\"notes\") <> LOWER(?) OR \"notes\" IS NULL)",
            "category in (\"laptop\", \"PHONE\")    | LOWER(\"cat\") IN (LOWER(?), LOWER(?))",
            "price == 10                            | \"price_usd\" = ?",
            "price in (1, 2.5, -3)                  | \"price_usd\" IN (?, ?, ?)",
            "active == true                         | \"is_active\" = ?",
            "created >= \"2026-01-01\"              | \"created_at\" >= ?",
            "created in (\"2026-01-01T09:00:00\", \"2026-02-01T10:00:00\") | \"created_at\" IN (?, ?)",
            "name < \"M\"                           | \"item_name\" < ?",
            "name ~ \"dun\"                         | \"item_name\" ILIKE ? ESCAPE '\\'",
    })
    void rendersByFieldType(String filter, String expectedWhere) {
        assertThat(sql(filter)).isEqualTo(expectedWhere);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "price == \"10\"                | expected a number",
            "price ~ \"1\"                  | only works on text fields",
            "name == 5                      | expected a \"text\" value",
            "active == \"yes\"              | expected true or false",
            "active > false                 | only support '==' and '!='",
            "created < \"last week\"        | expected a date",
            "created == 2026                | expected a date",
            "category == \"tablet\"         | is not one of [LAPTOP, PHONE, SMART_WATCH]",
            "category in (\"laptop\", \"tv\") | 'tv' is not one of",
            "name ~ 5                       | '~' needs a text value",
            "secret == \"x\"                | not declared or not queryable",
    })
    void rejectsTypeErrorsWithHelpfulMessages(String filter, String message) {
        assertThatThrownBy(() -> sql(filter))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining(message);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "category == \"smart_watch\"",
            "category ~ \"watch\"",
            "created > \"2026-01-31T10:15:00Z\"",
            "created > \"2026-01-31 10:15\"",
            "notes == 5",
            "notes ~ \"anything\"",
    })
    void acceptsLenientButValidForms(String filter) {
        assertThat(sql(filter)).isNotBlank();
    }
}
