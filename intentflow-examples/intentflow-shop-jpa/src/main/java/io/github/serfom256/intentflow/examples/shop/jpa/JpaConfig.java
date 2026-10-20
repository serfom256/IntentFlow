package io.github.serfom256.intentflow.examples.shop.jpa;

import io.github.serfom256.intentflow.examples.shop.store.EntityMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("jpa")
class JpaConfig {

    @Bean
    EntityMapper entityMapper() {
        return entity -> switch (entity) {
            case ProductEntity p -> p.toDomain();
            case CustomerEntity c -> c.toDomain();
            case OrderEntity o -> o.toDomain();
            default -> entity;
        };
    }
}
