package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.intentflow.core.pipeline.LlmRateLimiter;
import io.github.serfom256.intentflow.core.pipeline.factories.LlmRateLimiters;
import io.github.serfom256.intentflow.ratelimit.zookeeper.ZooKeeperRateLimiter;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "io.github.serfom256.intentflow.ratelimit.zookeeper.ZooKeeperRateLimiter")
@ConditionalOnProperty(prefix = "intentflow.llm.rate-limit", name = "store", havingValue = "zookeeper")
class ZooKeeperRateLimitConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    CuratorFramework intentFlowCurator(IntentFlowProperties properties) {
        String connect = properties.llm().rateLimit().zookeeper().connect();
        if (!StringUtils.hasText(connect)) {
            throw new IllegalStateException("intentflow.llm.rate-limit.store=zookeeper needs a CuratorFramework bean "
                    + "or intentflow.llm.rate-limit.zookeeper.connect, e.g. zk1:2181,zk2:2181");
        }
        CuratorFramework client = CuratorFrameworkFactory.newClient(connect, new ExponentialBackoffRetry(200, 3));
        client.start();
        return client;
    }

    @Bean
    @ConditionalOnMissingBean
    LlmRateLimiter intentFlowZooKeeperRateLimiter(CuratorFramework client, IntentFlowProperties properties) {
        IntentFlowProperties.Llm.RateLimit limit = properties.llm().rateLimit();
        if (limit.questions() <= 0) {
            return LlmRateLimiter.NONE;
        }
        return new ZooKeeperRateLimiter(client, limit.zookeeper().path(), limit.questions(), limit.period(),
                limit.per() == IntentFlowProperties.Llm.RateLimit.Per.TENANT ? LlmRateLimiters.byTenant()
                        : LlmRateLimiters.byPrincipal(),
                limit.zookeeper().failOpen(), Clock.systemUTC());
    }
}
