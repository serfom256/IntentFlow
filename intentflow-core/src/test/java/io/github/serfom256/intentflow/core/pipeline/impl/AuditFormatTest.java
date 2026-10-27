package io.github.serfom256.intentflow.core.pipeline.impl;

import io.github.serfom256.intentflow.core.api.TokenUsage;
import io.github.serfom256.intentflow.core.catalog.ServiceOverride;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceNames;
import io.github.serfom256.intentflow.core.pipeline.CatalogChangeAudit;
import io.github.serfom256.intentflow.core.pipeline.PipelineListener.Outcome;
import io.github.serfom256.intentflow.core.pipeline.QuestionAudit;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditFormatTest {

    private static final Instant AT = Instant.parse("2026-10-21T12:00:00Z");

    @Test
    void aQuestionIsOneLineOfKeyValues() {
        QuestionAudit audit = new QuestionAudit(AT, "ann", "acme", "my \"open\" orders", ServiceNames.of("orders"),
                Outcome.ANSWERED, "orders | filter status == \"OPEN\"", false,
                "SELECT * FROM \"order\" WHERE status = ?", List.of("OPEN"), 3, new TokenUsage(900, 20),
                Duration.ofMillis(1234), null);

        assertThat(Slf4jAuditLog.format(audit)).isEqualTo("question at=2026-10-21T12:00:00Z principal=ann tenant=acme"
                + " service=orders outcome=answered rows=3 cached=false tokens=920 elapsedMs=1234"
                + " question=\"my \\\"open\\\" orders\" dsl=\"orders | filter status == \\\"OPEN\\\"\""
                + " sql=\"SELECT * FROM \\\"order\\\" WHERE status = ?\"");
    }

    @Test
    void bindValuesAreNotWrittenAndNewlinesCannotForgeALine() {
        QuestionAudit audit = new QuestionAudit(AT, null, null, "books\nquestion principal=admin", null, Outcome.FAILED,
                null, false, null, List.of("secret"), 0, TokenUsage.NONE, Duration.ZERO, "database is down");

        String line = Slf4jAuditLog.format(audit);

        assertThat(line).doesNotContain("secret").doesNotContain("\n").contains("error=\"database is down\"")
                .doesNotContain("principal=null");
    }

    @Test
    void aCatalogChangeShowsBeforeAndAfter() {
        CatalogChangeAudit audit = new CatalogChangeAudit(AT, "admin", ServiceNames.of("products"),
                CatalogChangeAudit.Action.UPDATED, ServiceOverride.NONE,
                new ServiceOverride("Items in stock", null, Map.of()));

        assertThat(Slf4jAuditLog.format(audit))
                .startsWith("catalog-change at=2026-10-21T12:00:00Z user=admin "
                        + "service=products action=updated before=\"description=null")
                .contains("after=\"description=Items in stock, uri=null, fields={}\"");
    }

    @Test
    void valuesAreMaskedUnlessShown() {
        String dsl = "orders | filter customer == \"Ann \\\"A\\\" Smith\" and total > 99.5 | top 3 per city by total";

        assertThat(LogValues.dsl(false, dsl))
                .isEqualTo("orders | filter customer == ? and total > ? | top ? per city by total");
        assertThat(LogValues.dsl(true, dsl)).isEqualTo(dsl);
        assertThat(LogValues.text(false, "salary of ann")).isEqualTo("<13 chars>");
        assertThat(LogValues.values(false, List.of(1, "x"))).isEqualTo("<2 values>");
    }
}
