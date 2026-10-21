package io.github.serfom256.intentflow.core.pipeline.impl;

import io.github.serfom256.intentflow.core.api.UserText;
import io.github.serfom256.intentflow.core.ast.DslSource;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import io.github.serfom256.intentflow.core.pipeline.LlmException;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Function;

@Slf4j
public final class FallbackLlmClient implements LlmClient {

    private final LlmClient primary;
    private final LlmClient fallback;

    public FallbackLlmClient(LlmClient primary, LlmClient fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    @Override
    public DslSource generateDsl(UserText normalizedText, ServiceCatalog catalog) {
        return call(client -> client.generateDsl(normalizedText, catalog));
    }

    @Override
    public DslSource repairDsl(UserText normalizedText, ServiceCatalog catalog, DslSource failed, String error) {
        return call(client -> client.repairDsl(normalizedText, catalog, failed, error));
    }

    @Override
    public void ping() {
        primary.ping();
    }

    private DslSource call(Function<LlmClient, DslSource> work) {
        try {
            return work.apply(primary);
        } catch (LlmException primaryFailure) {
            log.warn("Primary LLM failed ({}), asking the fallback", primaryFailure.getMessage());
            try {
                return work.apply(fallback);
            } catch (RuntimeException fallbackFailure) {
                fallbackFailure.addSuppressed(primaryFailure);
                throw fallbackFailure;
            }
        }
    }
}
