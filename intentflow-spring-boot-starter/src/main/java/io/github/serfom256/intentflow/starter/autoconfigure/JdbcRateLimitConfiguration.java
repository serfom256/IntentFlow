package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.intentflow.core.pipeline.LlmRateLimiter;
import io.github.serfom256.intentflow.ratelimit.jdbc.JdbcRateLimiter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "io.github.serfom256.intentflow.ratelimit.jdbc.JdbcRateLimiter")
@ConditionalOnProperty(prefix = "intentflow.llm.rate-limit", name = "store", havingValue = "jdbc")
class JdbcRateLimitConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @DependsOnDatabaseInitialization
    LlmRateLimiter intentFlowJdbcRateLimiter(ObjectProvider<DataSource> dataSources,
            IntentFlowProperties properties) {
        IntentFlowProperties.Llm.RateLimit limit = properties.llm().rateLimit();
        if (limit.questions() <= 0) {
            return LlmRateLimiter.NONE;
        }
        DataSource dataSource = dataSources.getIfUnique();
        if (dataSource == null) {
            throw new IllegalStateException("intentflow.llm.rate-limit.store=jdbc needs a single DataSource; "
                    + "define an LlmRateLimiter bean with JdbcRateLimiter to choose one");
        }
        return new JdbcRateLimiter(dataSource, limit.jdbc().table(), limit.jdbc().createTable(), limit.questions(),
                limit.period(), limit.key(), limit.jdbc().failOpen(), Clock.systemUTC());
    }
}
