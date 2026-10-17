package io.github.serfom256.intentflow.examples.shop.jdbc;

import io.github.serfom256.intentflow.examples.shop.domain.Customer;
import io.github.serfom256.intentflow.examples.shop.domain.Order;
import io.github.serfom256.intentflow.examples.shop.domain.Product;
import io.github.serfom256.intentflow.examples.shop.store.CrudStore;
import io.github.serfom256.intentflow.examples.shop.store.EntityMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration(proxyBeanMethods = false)
@Profile("jdbc")
class JdbcConfig {

    @Bean
    CrudStore<Product> products(JdbcClient jdbc) {
        return new JdbcProducts(jdbc);
    }

    @Bean
    CrudStore<Customer> customers(JdbcClient jdbc) {
        return new JdbcCustomers(jdbc);
    }

    @Bean
    CrudStore<Order> orders(JdbcClient jdbc) {
        return new JdbcOrders(jdbc);
    }

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
