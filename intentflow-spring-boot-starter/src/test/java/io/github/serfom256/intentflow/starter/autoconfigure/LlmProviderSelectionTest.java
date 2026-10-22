package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import io.github.serfom256.intentflow.llm.anthropic.AnthropicLlmClient;
import io.github.serfom256.intentflow.llm.anthropic.AnthropicLlmClients;
import io.github.serfom256.intentflow.llm.azure.AzureOpenAiLlmClients;
import io.github.serfom256.intentflow.llm.bedrock.BedrockLlmClient;
import io.github.serfom256.intentflow.llm.bedrock.BedrockLlmClients;
import io.github.serfom256.intentflow.llm.gigachat.GigaChatLlmClients;
import io.github.serfom256.intentflow.llm.openai.OpenAiCompatibleLlmClient;
import io.github.serfom256.intentflow.llm.vertex.GeminiLlmClient;
import io.github.serfom256.intentflow.llm.vertex.GeminiLlmClients;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LlmProviderSelectionTest {

    private static final String FIXTURES = "io.github.serfom256.intentflow.starter.autoconfigure.fixtures";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IntentFlowAutoConfiguration.class))
            .withBean(QueryExecutor.class, () -> new QueryExecutor() {
                @Override
                public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
                    return List.of();
                }
            })
            .withPropertyValues("intentflow.llm.model=m", "intentflow.llm.api-key=k", "intentflow.llm.provider=",
                    "intentflow.llm.circuit-breaker.failure-threshold=0", "intentflow.catalog.packages=" + FIXTURES,
                    "intentflow.schema.introspect=false", "intentflow.dialect=postgresql");

    @Test
    void theOnlyProviderModuleIsUsed() {
        runner.withClassLoader(new FilteredClassLoader(AzureOpenAiLlmClients.class, GigaChatLlmClients.class,
                AnthropicLlmClients.class, GeminiLlmClients.class, BedrockLlmClients.class))
                .run(ctx -> assertThat(ctx.getBean(LlmClient.class)).isInstanceOf(OpenAiCompatibleLlmClient.class));
        runner.withClassLoader(new FilteredClassLoader(OpenAiCompatibleLlmClient.class, AzureOpenAiLlmClients.class,
                GigaChatLlmClients.class, GeminiLlmClients.class, BedrockLlmClients.class))
                .run(ctx -> assertThat(ctx.getBean(LlmClient.class)).isInstanceOf(AnthropicLlmClient.class));
    }

    @Test
    void azureBringsTheOpenAiModuleButIsStillTheOnlyProvider() {
        runner.withClassLoader(new FilteredClassLoader(GigaChatLlmClients.class, AnthropicLlmClients.class,
                GeminiLlmClients.class, BedrockLlmClients.class))
                .withPropertyValues("intentflow.llm.base-url=https://res.openai.azure.com")
                .run(ctx -> assertThat(ctx.getBean(LlmClient.class)).isInstanceOf(OpenAiCompatibleLlmClient.class));
    }

    @Test
    void severalProviderModulesNeedAChoice() {
        runner.run(ctx -> assertThat(ctx).hasFailed().getFailure().rootCause()
                .hasMessage("Several LLM provider modules are on the classpath [azure-openai, gigachat, anthropic, "
                        + "vertex, bedrock]: set intentflow.llm.provider to one of them."));
    }

    @Test
    void theProviderPropertyPicksOne() {
        runner.withPropertyValues("intentflow.llm.provider=bedrock", "intentflow.llm.bedrock.region=eu-central-1")
                .run(ctx -> assertThat(ctx.getBean(LlmClient.class)).isInstanceOf(BedrockLlmClient.class));
        runner.withPropertyValues("intentflow.llm.provider=vertex")
                .run(ctx -> assertThat(ctx.getBean(LlmClient.class)).isInstanceOf(GeminiLlmClient.class));
        runner.withPropertyValues("intentflow.llm.provider=anthropic", "intentflow.llm.anthropic.effort=low")
                .run(ctx -> assertThat(ctx.getBean(LlmClient.class)).isInstanceOf(AnthropicLlmClient.class));
        runner.withPropertyValues("intentflow.llm.provider=gigachat")
                .run(ctx -> assertThat(ctx.getBean(LlmClient.class)).isInstanceOf(OpenAiCompatibleLlmClient.class));
    }

    @Test
    void azureNeedsItsEndpoint() {
        runner.withPropertyValues("intentflow.llm.provider=azure-openai")
                .run(ctx -> assertThat(ctx).hasFailed().getFailure().rootCause()
                        .hasMessageContaining("Set intentflow.llm.base-url to the Azure OpenAI endpoint"));
    }
}
