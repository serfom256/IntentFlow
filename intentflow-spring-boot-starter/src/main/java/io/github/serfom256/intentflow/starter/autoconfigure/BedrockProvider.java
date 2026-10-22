package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.intentflow.llm.bedrock.BedrockLlmClients;
import io.github.serfom256.intentflow.llm.bedrock.BedrockSettings;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "io.github.serfom256.intentflow.llm.bedrock.BedrockLlmClients")
@ConditionalOnLlmProvider("bedrock")
class BedrockProvider {

    @Bean
    LlmClientFactory intentFlowBedrockClientFactory(IntentFlowProperties properties, ObjectProvider<Clock> clock) {
        IntentFlowProperties.Llm llm = properties.llm();
        IntentFlowProperties.Llm.Bedrock bedrock = llm.bedrock();
        BedrockSettings settings = new BedrockSettings(bedrock.region(), llm.maxTokens(), bedrock.temperature(),
                bedrock.promptCaching());
        Clock today = clock.getIfAvailable(Clock::systemDefaultZone);
        return (baseUrl, model, apiKey) -> BedrockLlmClients.of(model, settings, llm.timeout(), llm.maxRetries(),
                today);
    }
}
