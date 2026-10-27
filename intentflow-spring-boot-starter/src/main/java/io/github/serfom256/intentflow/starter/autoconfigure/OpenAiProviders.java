package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.intentflow.llm.azure.AzureOpenAiLlmClients;
import io.github.serfom256.intentflow.llm.gigachat.GigaChatLlmClients;
import io.github.serfom256.intentflow.llm.gigachat.GigaChatSettings;
import io.github.serfom256.intentflow.llm.openai.LlmHttpOptions;
import io.github.serfom256.intentflow.llm.openai.OpenAiCompatibleLlmClient;
import io.github.serfom256.intentflow.llm.openai.OpenAiLlmClients;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.time.Clock;

final class OpenAiProviders {

    private static final URI LOCAL_OPENAI = URI.create("http://localhost:1234/v1");

    private OpenAiProviders() {
    }

    static LlmHttpOptions httpOptions(IntentFlowProperties properties) {
        IntentFlowProperties.Llm llm = properties.llm();
        ProxySelector proxy = llm.proxy() == null ? null
                : ProxySelector.of(new InetSocketAddress(llm.proxy().getHost(), llm.proxy().getPort()));
        return new LlmHttpOptions(llm.apiKeyHeader(), llm.headers(), proxy, llm.maxRetries(), llm.retryBackoff(),
                properties.logging().values());
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(OpenAiCompatibleLlmClient.class)
    @ConditionalOnLlmProvider(LlmProviders.OPENAI)
    static class OpenAi {

        @Bean
        LlmClientFactory intentFlowOpenAiClientFactory(IntentFlowProperties properties, ObjectProvider<Clock> clock) {
            IntentFlowProperties.Llm llm = properties.llm();
            LlmHttpOptions options = httpOptions(properties);
            Clock today = clock.getIfAvailable(Clock::systemDefaultZone);
            return (baseUrl, model, apiKey) -> OpenAiLlmClients.of(baseUrl != null ? baseUrl : LOCAL_OPENAI, model,
                    apiKey, llm.timeout(), today, options);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.github.serfom256.intentflow.llm.azure.AzureOpenAiLlmClients")
    @ConditionalOnLlmProvider("azure-openai")
    static class AzureOpenAi {

        @Bean
        LlmClientFactory intentFlowAzureOpenAiClientFactory(IntentFlowProperties properties,
                ObjectProvider<Clock> clock) {
            IntentFlowProperties.Llm llm = properties.llm();
            LlmHttpOptions options = httpOptions(properties);
            Clock today = clock.getIfAvailable(Clock::systemDefaultZone);
            return (baseUrl, model, apiKey) -> {
                if (baseUrl == null) {
                    throw new IllegalStateException("Set intentflow.llm.base-url to the Azure OpenAI endpoint, "
                            + "e.g. https://my-resource.openai.azure.com");
                }
                return AzureOpenAiLlmClients.of(baseUrl, model, apiKey, llm.timeout(), today, options);
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.github.serfom256.intentflow.llm.gigachat.GigaChatLlmClients")
    @ConditionalOnLlmProvider("gigachat")
    static class GigaChat {

        @Bean
        LlmClientFactory intentFlowGigaChatClientFactory(IntentFlowProperties properties, ObjectProvider<Clock> clock) {
            IntentFlowProperties.Llm llm = properties.llm();
            IntentFlowProperties.Llm.GigaChat gigachat = llm.gigachat();
            LlmHttpOptions options = httpOptions(properties);
            Clock today = clock.getIfAvailable(Clock::systemDefaultZone);
            SSLContext ssl = gigachat.trustStore() == null ? null
                    : GigaChatLlmClients.trustStore(gigachat.trustStore(), gigachat.trustStorePassword(),
                            gigachat.trustStoreType());
            return (baseUrl, model, apiKey) -> GigaChatLlmClients.of(model, apiKey,
                    new GigaChatSettings(baseUrl != null ? baseUrl : GigaChatSettings.BASE_URL,
                            gigachat.oauthUrl() != null ? gigachat.oauthUrl() : GigaChatSettings.OAUTH_URL,
                            gigachat.scope(), ssl),
                    llm.timeout(), today, options);
        }
    }
}
