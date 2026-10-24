package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.intentflow.core.pipeline.LlmRateLimiter;
import io.github.serfom256.intentflow.ratelimit.redis.RedisRateLimiter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = { "io.github.serfom256.intentflow.ratelimit.redis.RedisRateLimiter",
        "org.springframework.data.redis.core.StringRedisTemplate" })
@ConditionalOnProperty(prefix = "intentflow.llm.rate-limit", name = "store", havingValue = "redis")
class RedisRateLimitConfiguration {

    @Bean
    @ConditionalOnMissingBean
    LlmRateLimiter intentFlowRedisRateLimiter(ObjectProvider<RedisConnectionFactory> connections,
            IntentFlowProperties properties) {
        IntentFlowProperties.Llm.RateLimit limit = properties.llm().rateLimit();
        if (limit.questions() <= 0) {
            return LlmRateLimiter.NONE;
        }
        RedisConnectionFactory connection = connections.getIfAvailable();
        if (connection == null) {
            throw new IllegalStateException("intentflow.llm.rate-limit.store=redis needs a Redis connection: add "
                    + "spring-boot-starter-data-redis and set spring.data.redis.host or spring.data.redis.url");
        }
        return new RedisRateLimiter(new StringRedisTemplate(connection), limit.redis().prefix(), limit.questions(),
                limit.period(), limit.key(), limit.redis().failOpen(), Clock.systemUTC());
    }
}
