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
import io.github.serfom256.intentflow.core.schema.SchemaFacts;
import io.github.serfom256.intentflow.core.schema.TableFacts;
import io.github.serfom256.intentflow.core.sql.Optimization;
import io.github.serfom256.intentflow.core.testing.TestDialect;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TimeZoneTest {

    @IntentService(value = "events", table = "event", uri = "/events/{{id}}", defaultSort = "id")
    record Event(@IntentField long id, @IntentField LocalDateTime happenedAt, @IntentField LocalDateTime localAt) {
    }

    private static final ServiceCatalog CATALOG = ServiceCatalogs.fromAnnotated(Event.class);
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final SchemaFacts FACTS = SchemaFacts.of(Map.of("event",
            new TableFacts(List.of("id"), List.of(), Set.of(), Map.of(), Set.of("happened_at"))), Map.of());

    private static BoundQuery sql(SqlDialect dialect, String filter) {
        return SqlBuilders.create(dialect, FACTS, EnumSet.allOf(Optimization.class), BERLIN)
                .build(AstValidators.create().validate(
                        DslParsers.create().parse(DslSources.of("events | filter " + filter)), CATALOG));
    }

    @Test
    void wholeDaysOfAZonedColumnStartAtMidnightInTheConfiguredZone() {
        BoundQuery query = sql(new TestDialect(), "happened_at == \"2026-10-10\"");

        assertThat(query.sql()).contains("(\"happened_at\" >= ? AND \"happened_at\" < ?)");
        assertThat(query.values()).containsExactly(OffsetDateTime.parse("2026-10-10T00:00+02:00"),
                OffsetDateTime.parse("2026-10-11T00:00+02:00"));
    }

    @Test
    void aLocalColumnIsComparedWithTheWallClockTime() {
        assertThat(sql(new TestDialect(), "local_at >= \"2026-01-15\"").values())
                .containsExactly(LocalDateTime.parse("2026-01-15T00:00"));
        assertThat(sql(new TestDialect(), "local_at < \"2026-07-01T12:00:00+05:00\"").values())
                .containsExactly(LocalDateTime.parse("2026-07-01T09:00"));
    }

    @Test
    void calendarPartsOfAZonedColumnAreTakenInTheConfiguredZone() {
        assertThat(sql(new TestDialect(), "hour(happened_at) == 9").sql())
                .contains("EXTRACT(HOUR FROM (\"happened_at\" AT TIME ZONE 'Europe/Berlin')) = ?");
        assertThat(sql(new TestDialect(), "hour(local_at) == 9").sql())
                .contains("EXTRACT(HOUR FROM \"local_at\") = ?");
    }
}
