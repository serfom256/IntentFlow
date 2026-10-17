package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ast.AstQuery;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceDescriptors;
import io.github.serfom256.intentflow.core.pipeline.factories.AstValidators;
import io.github.serfom256.intentflow.core.pipeline.factories.DslParsers;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.dialect.postgresql.PostgresDialect;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class DateLiteralTest {

    @IntentService(value = "orders", uri = "/orders/{{id}}")
    record Order(@IntentField long id, @IntentField LocalDateTime createdAt) {
    }

    private static final ServiceCatalog CATALOG = ServiceCatalogs.of(ServiceDescriptors.fromAnnotated(Order.class));

    private static BoundQuery sql(String filter) {
        AstQuery ast = DslParsers.create().parse(DslSources.of("orders | filter " + filter));
        return SqlBuilders.create(new PostgresDialect()).build(AstValidators.create().validate(ast, CATALOG));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "created_at == \"2026-10-10\" | (\"created_at\" >= ? AND \"created_at\" < ?) | 2026-10-10T00:00,2026-10-11T00:00",
            "created_at != \"2026-10-10\" | ((\"created_at\" < ? OR \"created_at\" >= ?) OR \"created_at\" IS NULL) | 2026-10-10T00:00,2026-10-11T00:00",
            "created_at > \"2026-10-10\"  | \"created_at\" >= ? | 2026-10-11T00:00",
            "created_at <= \"2026-10-10\" | \"created_at\" < ? | 2026-10-11T00:00",
            "created_at < \"2026-10-10\"  | \"created_at\" < ? | 2026-10-10T00:00",
            "created_at >= \"2026-10-10\" | \"created_at\" >= ? | 2026-10-10T00:00",
            "created_at == \"2026-10-10T12:00:00\" | \"created_at\" = ? | 2026-10-10T12:00",
            "created_at == \"2026-12-31\" | (\"created_at\" >= ? AND \"created_at\" < ?) | 2026-12-31T00:00,2027-01-01T00:00",
    })
    void aDateWithoutTimeMeansTheWholeDay(String filter, String where, String binds) {
        BoundQuery query = sql(filter);

        assertThat(query.sql()).isEqualTo("SELECT * FROM \"order\" WHERE " + where + " LIMIT 50");
        assertThat(query.values()).containsExactly(Arrays.stream(binds.split(",")).map(LocalDateTime::parse).toArray());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "created_at in (\"2026-10-10\", \"2026-10-12T08:00:00\") | ((\"created_at\" >= ? AND \"created_at\" < ?) OR \"created_at\" = ?)",
    })
    void inWithDatesMatchesAnyOfTheDays(String filter, String where) {
        assertThat(sql(filter).sql()).isEqualTo("SELECT * FROM \"order\" WHERE " + where + " LIMIT 50");
    }
}
