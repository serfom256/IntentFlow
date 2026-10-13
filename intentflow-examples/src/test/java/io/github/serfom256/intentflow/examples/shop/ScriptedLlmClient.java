package io.github.serfom256.intentflow.examples.shop;

import io.github.serfom256.intentflow.core.api.UserText;
import io.github.serfom256.intentflow.core.ast.DslSource;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.pipeline.LlmClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class ScriptedLlmClient implements LlmClient {

    private final Map<String, Supplier<String>> script = new ConcurrentHashMap<>();
    private final AtomicInteger calls = new AtomicInteger();
    private volatile List<String> lastCatalog = List.of();

    public ScriptedLlmClient answer(String question, String dsl) {
        script.put(question, () -> dsl);
        return this;
    }

    public ScriptedLlmClient fail(String question, RuntimeException error) {
        script.put(question, () -> {
            throw error;
        });
        return this;
    }

    public int calls() {
        return calls.get();
    }

    public List<String> lastCatalog() {
        return lastCatalog;
    }

    @Override
    public DslSource generateDsl(UserText normalizedText, ServiceCatalog catalog) {
        calls.incrementAndGet();
        lastCatalog = catalog.names().stream().map(ServiceName::value).sorted().toList();
        Supplier<String> answer = script.get(normalizedText.value());
        if (answer == null) {
            throw new IllegalStateException("No scripted answer for '" + normalizedText.value() + "'");
        }
        return DslSources.of(answer.get());
    }
}
