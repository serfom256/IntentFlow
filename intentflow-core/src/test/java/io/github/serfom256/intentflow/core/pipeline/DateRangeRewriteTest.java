package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.pipeline.factories.AstValidators;
import io.github.serfom256.intentflow.core.pipeline.factories.DslParsers;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.core.testing.TestDialect;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class DateRangeRewriteTest {

    @IntentService(value = "events", table = "event", uri = "/events/{{id}}", defaultSort = "id")
    record Event(@IntentField long id, @IntentField Instant at, @IntentField int year) {
    }

    private static final ServiceCatalog CATALOG = ServiceCatalogs.fromAnnotated(Event.class);

    private static BoundQuery sql(String filter) {
        return SqlBuilders.create(new TestDialect()).build(AstValidators.create()
                .validate(DslParsers.create().parse(DslSources.of("events | filter " + filter)), CATALOG));
    }

    private static String where(BoundQuery query) {
        String sql = query.sql();
        return sql.substring(sql.indexOf(" WHERE ") + 7, sql.indexOf(" ORDER BY "));
    }

    private static String binds(BoundQuery query) {
        return query.values().stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "year(at) == 2025                  | (\"at\" >= # AND \"at\" < #)        | 2025-01-01T00:00,2026-01-01T00:00",
            "year(at) != 2025                  | ((\"at\" < # OR \"at\" >= #) OR \"at\" IS NULL) | 2025-01-01T00:00,2026-01-01T00:00",
            "year(at) < 2025                   | \"at\" < #                          | 2025-01-01T00:00",
            "year(at) <= 2025                  | \"at\" < #                          | 2026-01-01T00:00",
            "year(at) > 2025                   | \"at\" >= #                         | 2026-01-01T00:00",
            "year(at) >= 2025                  | \"at\" >= #                         | 2025-01-01T00:00",
            "year(at) == 2024 and month(at) == 2 | (\"at\" >= # AND \"at\" < #)      | 2024-02-01T00:00,2024-03-01T00:00",
            "month(at) == 2 and year(at) == 2024 and day(at) == 29 | (\"at\" >= # AND \"at\" < #) | 2024-02-29T00:00,2024-03-01T00:00",
            "date_trunc(\"hour\", at) == \"2026-10-17T09:00:00\" | (\"at\" >= # AND \"at\" < #) | 2026-10-17T09:00,2026-10-17T10:00",
            "date_trunc(\"year\", at) >= \"2025-01-01\" | \"at\" >= #                 | 2025-01-01T00:00",
            "not (year(at) == 2025) or id == 1 | ((NOT ((\"at\" >= # AND \"at\" < #)) OR \"at\" IS NULL) OR \"id\" = ?) | 2025-01-01T00:00,2026-01-01T00:00,1",
            "year(at) == 2024 and month(at) == 2 and day(at) == 30 | (\"at\" >= # AND \"at\" < # AND EXTRACT(MONTH FROM \"at\") = ? AND EXTRACT(DAY FROM \"at\") = ?) | 2024-01-01T00:00,2025-01-01T00:00,2,30" })
    void comparisonsOnACalendarPartBecomeRanges(String filter, String where, String binds) {
        BoundQuery query = sql(filter);

        assertThat(where(query)).isEqualTo(where.replace("#", "?"));
        assertThat(binds(query)).isEqualTo(binds);
        assertThat(query.optimizations()).contains("date-ranges");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "month(at) == 2",
            "year(at) == 2025.5",
            "date_trunc(\"month\", at) == \"2026-10-17\"",
            "year == 2025",
            "hour(at) == 9" })
    void leavesComparisonsThatAreNotRangesUnchanged(String filter) {
        assertThat(sql(filter).optimizations()).doesNotContain("date-ranges");
    }
}
