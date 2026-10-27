package io.github.serfom256.intentflow.examples.shop.jimmer;

import io.github.serfom256.intentflow.examples.shop.domain.Customer;
import io.github.serfom256.intentflow.examples.shop.domain.Order;
import io.github.serfom256.intentflow.examples.shop.domain.Product;
import io.github.serfom256.intentflow.examples.shop.store.EntityMapper;
import org.babyfish.jimmer.sql.EnumType;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.dialect.Dialect;
import org.babyfish.jimmer.sql.dialect.MySqlDialect;
import org.babyfish.jimmer.sql.dialect.PostgresDialect;
import org.babyfish.jimmer.sql.fetcher.Fetcher;
import org.babyfish.jimmer.sql.runtime.ConnectionManager;
import org.babyfish.jimmer.sql.runtime.DefaultDatabaseNamingStrategy;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.DataSourceUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.function.Function;

@Configuration(proxyBeanMethods = false)
@Profile("jimmer")
class JimmerConfig {

    @Bean
    JSqlClient jimmerSqlClient(DataSource dataSource) throws SQLException {
        return JSqlClient.newBuilder().setConnectionManager(springManaged(dataSource)).setDialect(dialect(dataSource))
                .setDatabaseNamingStrategy(DefaultDatabaseNamingStrategy.LOWER_CASE)
                .setDefaultEnumStrategy(EnumType.Strategy.NAME).build();
    }

    @Bean
    Fetcher<OrderEntity> orderFetcher() {
        return OrderEntityFetcher.$.allScalarFields().customerId();
    }

    @Bean
    EntityMapper entityMapper() {
        return entity -> switch (entity) {
        case ProductEntity p -> new Product(p.id(), p.name(), p.brand(), p.category(), p.price(), p.discount(),
                p.rating(), p.stock(), p.active(), p.supplierCost(), p.createdAt());
        case CustomerEntity c ->
            new Customer(c.id(), c.fullName(), c.email(), c.city(), c.country(), c.vip(), c.createdAt());
        case OrderEntity o -> new Order(o.id(), o.customerId(), o.status(), o.total(), o.itemsCount(), o.createdAt());
        default -> entity;
        };
    }

    private static ConnectionManager springManaged(DataSource dataSource) {
        return new ConnectionManager() {
            @Override
            public <R> R execute(@Nullable Connection connection, Function<Connection, R> block) {
                if (connection != null) {
                    return block.apply(connection);
                }
                Connection managed = DataSourceUtils.getConnection(dataSource);
                try {
                    return block.apply(managed);
                } finally {
                    DataSourceUtils.releaseConnection(managed, dataSource);
                }
            }
        };
    }

    private static Dialect dialect(DataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
            return product.contains("mysql") ? new MySqlDialect() : new PostgresDialect();
        }
    }
}
