package io.github.serfom256.intentflow.core.pipeline.impl;

import io.github.serfom256.intentflow.core.catalog.ServiceOverride;
import io.github.serfom256.intentflow.core.pipeline.AuditLog;
import io.github.serfom256.intentflow.core.pipeline.CatalogChangeAudit;
import io.github.serfom256.intentflow.core.pipeline.QuestionAudit;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public final class Slf4jAuditLog implements AuditLog {

    private static final Logger AUDIT = LoggerFactory.getLogger("intentflow.audit");

    @Override
    public void question(QuestionAudit audit) {
        if (AUDIT.isInfoEnabled()) {
            AUDIT.info(format(audit));
        }
    }

    @Override
    public void catalogChange(CatalogChangeAudit audit) {
        if (AUDIT.isInfoEnabled()) {
            AUDIT.info(format(audit));
        }
    }

    static String format(QuestionAudit audit) {
        StringBuilder line = new StringBuilder("question");
        field(line, "at", audit.at().toString());
        field(line, "principal", audit.principal());
        field(line, "tenant", audit.tenant());
        field(line, "service", audit.service() == null ? null : audit.service().value());
        field(line, "outcome", audit.outcome().name().toLowerCase(Locale.ROOT));
        field(line, "rows", String.valueOf(audit.rows()));
        field(line, "cached", String.valueOf(audit.cached()));
        field(line, "tokens", String.valueOf(audit.tokens().total()));
        field(line, "elapsedMs", String.valueOf(audit.elapsed().toMillis()));
        field(line, "question", audit.question());
        field(line, "dsl", audit.dsl());
        field(line, "sql", audit.sql());
        field(line, "error", audit.error());
        return line.toString();
    }

    static String format(CatalogChangeAudit audit) {
        StringBuilder line = new StringBuilder("catalog-change");
        field(line, "at", audit.at().toString());
        field(line, "user", audit.user());
        field(line, "service", audit.service().value());
        field(line, "action", audit.action().name().toLowerCase(Locale.ROOT));
        field(line, "before", describe(audit.before()));
        field(line, "after", describe(audit.after()));
        return line.toString();
    }

    private static String describe(ServiceOverride override) {
        return "description=" + override.description() + ", uri=" + override.uri() + ", fields="
                + override.fieldDescriptions();
    }

    private static void field(StringBuilder line, String name, @Nullable String value) {
        if (value == null) {
            return;
        }
        line.append(' ').append(name).append('=');
        if (value.chars().anyMatch(c -> c == ' ' || c == '"' || c == '=' || c < 0x20)) {
            line.append('"')
                    .append(value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r"))
                    .append('"');
        } else {
            line.append(value);
        }
    }
}
