package io.github.serfom256.intentflow.core.pipeline.impl;

import io.github.serfom256.intentflow.core.api.UserText;
import io.github.serfom256.intentflow.core.ast.DslSource;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import io.github.serfom256.intentflow.core.pipeline.LlmException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class StubLlmClient implements LlmClient {

    private final Map<UserText, DslSource> responses;

    public StubLlmClient(Map<UserText, DslSource> responses) {
        this.responses = new ConcurrentHashMap<>(responses);
    }

    @Override
    public DslSource generateDsl(UserText normalizedText, ServiceCatalog catalog) {
        DslSource result = responses.get(normalizedText);
        if (result == null) {
            throw new LlmException("StubLlmClient has no response for: '" + normalizedText.value() + "'");
        }
        return result;
    }

    public void register(UserText text, DslSource dsl) {
        responses.put(text, dsl);
    }
}
