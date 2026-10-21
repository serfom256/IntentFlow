package io.github.serfom256.intentflow.core.pipeline.impl;

import io.github.serfom256.intentflow.core.api.UserText;
import io.github.serfom256.intentflow.core.ast.DslSource;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import io.github.serfom256.intentflow.core.pipeline.LlmException;
import io.github.serfom256.intentflow.core.pipeline.LlmUnavailableException;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class ConcurrencyLimitedLlmClient implements LlmClient {

    private final LlmClient delegate;
    private final int maxCalls;
    private final Duration maxWait;
    private final Semaphore permits;

    public ConcurrencyLimitedLlmClient(LlmClient delegate, int maxCalls, Duration maxWait) {
        if (maxCalls < 1) {
            throw new IllegalArgumentException("maxCalls must be positive: " + maxCalls);
        }
        if (maxWait.isNegative()) {
            throw new IllegalArgumentException("maxWait must not be negative: " + maxWait);
        }
        this.delegate = delegate;
        this.maxCalls = maxCalls;
        this.maxWait = maxWait;
        this.permits = new Semaphore(maxCalls, true);
    }

    @Override
    public DslSource generateDsl(UserText normalizedText, ServiceCatalog catalog) {
        return call(() -> delegate.generateDsl(normalizedText, catalog));
    }

    @Override
    public DslSource repairDsl(UserText normalizedText, ServiceCatalog catalog, DslSource failed, String error) {
        return call(() -> delegate.repairDsl(normalizedText, catalog, failed, error));
    }

    @Override
    public void ping() {
        delegate.ping();
    }

    private DslSource call(Supplier<DslSource> work) {
        try {
            if (!permits.tryAcquire(maxWait.toNanos(), TimeUnit.NANOSECONDS)) {
                throw new LlmUnavailableException(
                        "LLM is busy: the limit of " + maxCalls + " concurrent calls is reached", null);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Interrupted while waiting for the LLM", e);
        }
        try {
            return work.get();
        } finally {
            permits.release();
        }
    }
}
