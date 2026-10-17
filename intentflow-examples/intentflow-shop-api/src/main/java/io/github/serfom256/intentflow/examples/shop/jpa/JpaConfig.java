package io.github.serfom256.intentflow.examples.shop.jpa;

import io.github.serfom256.intentflow.examples.shop.domain.Customer;
import io.github.serfom256.intentflow.examples.shop.domain.Order;
import io.github.serfom256.intentflow.examples.shop.domain.Product;
import io.github.serfom256.intentflow.examples.shop.store.CrudStore;
import io.github.serfom256.intentflow.examples.shop.store.EntityMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("jpa")
class JpaConfig {

    @Bean
    CrudStore<Product> products(ProductRepository repository) {
        return new JpaProducts(repository);
    }

    @Bean
    CrudStore<Customer> customers(CustomerRepository repository) {
        return new JpaCustomers(repository);
    }

    @Bean
    CrudStore<Order> orders(OrderRepository repository, CustomerRepository customers) {
        return new JpaOrders(repository, customers);
    }

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
