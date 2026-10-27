package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.intentflow.llm.vertex.GeminiLlmClients;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "io.github.serfom256.intentflow.llm.vertex.GeminiLlmClients")
@ConditionalOnLlmProvider("vertex")
class VertexProvider {

    @Bean
    LlmClientFactory intentFlowVertexClientFactory(IntentFlowProperties properties, ObjectProvider<Clock> clock) {
        IntentFlowProperties.Llm llm = properties.llm();
        IntentFlowProperties.Llm.Vertex vertex = llm.vertex();
        Clock today = clock.getIfAvailable(Clock::systemDefaultZone);
        return (baseUrl, model,
                apiKey) -> vertex.project() != null
                        ? GeminiLlmClients.vertex(vertex.project(), vertex.location(), model, llm.maxTokens(),
                                llm.timeout(), today)
                        : GeminiLlmClients.apiKey(baseUrl, apiKey, model, llm.maxTokens(), llm.timeout(), today);
    }
}
