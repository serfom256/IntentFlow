package io.github.serfom256.intentflow.examples.shop.jdbc;

import io.github.serfom256.intentflow.examples.shop.store.EntityMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("jdbc")
class JdbcConfig {

    @Bean
    EntityMapper entityMapper() {
        return entity -> switch (entity) {
        case ProductRow p -> p.toDomain();
        case CustomerRow c -> c.toDomain();
        case OrderRow o -> o.toDomain();
        default -> entity;
        };
    }
}
