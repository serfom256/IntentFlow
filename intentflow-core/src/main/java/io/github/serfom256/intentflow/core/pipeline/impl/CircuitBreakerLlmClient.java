package io.github.serfom256.intentflow.core.pipeline.impl;

import io.github.serfom256.intentflow.core.api.UserText;
import io.github.serfom256.intentflow.core.ast.DslSource;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import io.github.serfom256.intentflow.core.pipeline.LlmException;
import io.github.serfom256.intentflow.core.pipeline.LlmUnavailableException;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.util.function.Supplier;

@Slf4j
public final class CircuitBreakerLlmClient implements LlmClient {

    private final LlmClient delegate;
    private final int failureThreshold;
    private final Duration openFor;
    private final Clock clock;

    private int failures;
    private long openUntil = -1;
    private boolean trialInFlight;

    public CircuitBreakerLlmClient(LlmClient delegate, int failureThreshold, Duration openFor, Clock clock) {
        if (failureThreshold < 1) {
            throw new IllegalArgumentException("failureThreshold must be positive: " + failureThreshold);
        }
        if (!openFor.isPositive()) {
            throw new IllegalArgumentException("openFor must be positive: " + openFor);
        }
        this.delegate = delegate;
        this.failureThreshold = failureThreshold;
        this.openFor = openFor;
        this.clock = clock;
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
        boolean trial = admit();
        try {
            DslSource dsl = work.get();
            succeeded();
            return dsl;
        } catch (LlmUnavailableException e) {
            released(trial);
            throw e;
        } catch (LlmException e) {
            failed(trial);
            throw e;
        } catch (RuntimeException e) {
            released(trial);
            throw e;
        }
    }

    private synchronized boolean admit() {
        if (openUntil < 0) {
            return false;
        }
        long left = openUntil - clock.millis();
        if (left > 0 || trialInFlight) {
            Duration retryAfter = Duration.ofMillis(Math.max(left, 0));
            throw new LlmUnavailableException(
                    "LLM circuit is open after repeated failures (threshold " + failureThreshold + ")", retryAfter);
        }
        trialInFlight = true;
        return true;
    }

    private synchronized void succeeded() {
        if (openUntil >= 0) {
            log.info("LLM answered again, closing the circuit");
        }
        failures = 0;
        openUntil = -1;
        trialInFlight = false;
    }

    private synchronized void failed(boolean trial) {
        failures++;
        if (trial || (openUntil < 0 && failures >= failureThreshold)) {
            if (!trial) {
                log.warn("Opening the LLM circuit for {} after {} failed call(s) in a row", openFor, failures);
            }
            openUntil = clock.millis() + openFor.toMillis();
        }
        if (trial) {
            trialInFlight = false;
        }
    }

    private synchronized void released(boolean trial) {
        if (trial) {
            trialInFlight = false;
        }
    }
}
