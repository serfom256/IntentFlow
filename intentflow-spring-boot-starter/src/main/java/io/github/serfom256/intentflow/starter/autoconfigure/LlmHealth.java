package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

final class LlmHealth {

    enum Status {
        UP, DOWN, UNKNOWN
    }

    record Result(Status status, @Nullable String error) {
    }

    private final LlmClient llm;
    private final Duration interval;
    private @Nullable Result last;
    private long checkedAt;

    LlmHealth(LlmClient llm, Duration interval) {
        this.llm = llm;
        this.interval = interval;
    }

    synchronized Result check() {
        long now = System.nanoTime();
        if (last == null || now - checkedAt >= interval.toNanos()) {
            last = probe();
            checkedAt = now;
        }
        return last;
    }

    private Result probe() {
        try {
            llm.ping();
            return new Result(Status.UP, null);
        } catch (UnsupportedOperationException e) {
            return new Result(Status.UNKNOWN, null);
        } catch (RuntimeException e) {
            return new Result(Status.DOWN, e.getMessage());
        }
    }
}
