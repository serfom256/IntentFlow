package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.api.TokenUsage;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.pipeline.PipelineListener.Outcome;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record QuestionAudit(Instant at, @Nullable String principal, @Nullable String tenant, String question,
        @Nullable ServiceName service, Outcome outcome, @Nullable String dsl, boolean cached, @Nullable String sql,
        List<@Nullable Object> parameters, int rows, TokenUsage tokens, Duration elapsed, @Nullable String error) {

    public QuestionAudit {
        parameters = Collections.unmodifiableList(new ArrayList<>(parameters));
    }
}
