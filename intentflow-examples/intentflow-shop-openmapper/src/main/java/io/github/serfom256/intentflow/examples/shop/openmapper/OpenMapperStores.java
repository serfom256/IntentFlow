package io.github.serfom256.intentflow.examples.shop.openmapper;

import io.github.serfom256.intentflow.examples.shop.domain.Customer;
import io.github.serfom256.intentflow.examples.shop.domain.Order;
import io.github.serfom256.intentflow.examples.shop.domain.Product;
import io.github.serfom256.intentflow.examples.shop.store.CrudStore;
import io.github.serfom256.mappers.ColumnTypeRegistry;
import io.github.serfom256.procedures.Procedures;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenMapperStores {

    @Bean
    CrudStore<Product> products(Procedures procedures, ColumnTypeRegistry columnTypes) {
        return new OpenMapperStore<>(procedures, columnTypes, ProductEntity.class, ProductEntity::from,
                ProductEntity::toDomain);
    }

    @Bean
    CrudStore<Customer> customers(Procedures procedures, ColumnTypeRegistry columnTypes) {
        return new OpenMapperStore<>(procedures, columnTypes, CustomerEntity.class, CustomerEntity::from,
                CustomerEntity::toDomain);
    }

    @Bean
    CrudStore<Order> orders(Procedures procedures, ColumnTypeRegistry columnTypes) {
        return new OpenMapperStore<>(procedures, columnTypes, OrderEntity.class, OrderEntity::from,
                OrderEntity::toDomain);
    }
}
