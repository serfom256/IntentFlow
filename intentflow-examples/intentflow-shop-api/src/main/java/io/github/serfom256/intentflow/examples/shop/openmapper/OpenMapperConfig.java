package io.github.serfom256.intentflow.examples.shop.openmapper;

import io.github.serfom256.intentflow.examples.shop.domain.Customer;
import io.github.serfom256.intentflow.examples.shop.domain.Order;
import io.github.serfom256.intentflow.examples.shop.domain.Product;
import io.github.serfom256.intentflow.examples.shop.store.CrudStore;
import io.github.serfom256.intentflow.examples.shop.store.EntityMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("!jpa & !jimmer & !jdbc")
class OpenMapperConfig {

    @Bean
    CrudStore<Product> products(ProductDao dao) {
        return new OpenMapperProducts(dao);
    }

    @Bean
    CrudStore<Customer> customers(CustomerDao dao) {
        return new OpenMapperCustomers(dao);
    }

    @Bean
    CrudStore<Order> orders(OrderDao dao) {
        return new OpenMapperOrders(dao);
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
