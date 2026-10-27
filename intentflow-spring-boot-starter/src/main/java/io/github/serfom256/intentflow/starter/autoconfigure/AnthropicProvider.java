package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.intentflow.llm.anthropic.AnthropicLlmClients;
import io.github.serfom256.intentflow.llm.anthropic.AnthropicSettings;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "io.github.serfom256.intentflow.llm.anthropic.AnthropicLlmClients")
@ConditionalOnLlmProvider("anthropic")
class AnthropicProvider {

    @Bean
    LlmClientFactory intentFlowAnthropicClientFactory(IntentFlowProperties properties, ObjectProvider<Clock> clock) {
        IntentFlowProperties.Llm llm = properties.llm();
        AnthropicSettings settings = new AnthropicSettings(llm.maxTokens(), llm.anthropic().effort(),
                llm.anthropic().refusalFallback());
        Clock today = clock.getIfAvailable(Clock::systemDefaultZone);
        return (baseUrl, model, apiKey) -> AnthropicLlmClients.of(baseUrl, model, apiKey, settings, llm.timeout(),
                llm.maxRetries(), proxy(llm), today);
    }

    private static @Nullable Proxy proxy(IntentFlowProperties.Llm llm) {
        return llm.proxy() == null ? null
                : new Proxy(Proxy.Type.HTTP, new InetSocketAddress(llm.proxy().getHost(), llm.proxy().getPort()));
    }
}
