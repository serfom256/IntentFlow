package io.github.serfom256.intentflow.examples.shop.jimmer;

import io.github.serfom256.intentflow.examples.shop.domain.Customer;
import io.github.serfom256.intentflow.examples.shop.domain.Order;
import io.github.serfom256.intentflow.examples.shop.domain.Product;
import io.github.serfom256.intentflow.examples.shop.store.CrudStore;
import io.github.serfom256.intentflow.examples.shop.store.EntityMapper;
import org.babyfish.jimmer.sql.EnumType;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.dialect.Dialect;
import org.babyfish.jimmer.sql.dialect.MySqlDialect;
import org.babyfish.jimmer.sql.dialect.PostgresDialect;
import org.babyfish.jimmer.sql.fetcher.Fetcher;
import org.babyfish.jimmer.sql.runtime.ConnectionManager;
import org.babyfish.jimmer.sql.runtime.DefaultDatabaseNamingStrategy;
import org.babyfish.jimmer.sql.runtime.ExceptionTranslator;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator;
import org.springframework.jdbc.support.SQLExceptionTranslator;

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
        return JSqlClient.newBuilder()
                .setConnectionManager(springManaged(dataSource))
                .setDialect(dialect(dataSource))
                .setDatabaseNamingStrategy(DefaultDatabaseNamingStrategy.LOWER_CASE)
                .setDefaultEnumStrategy(EnumType.Strategy.NAME)
                .addExceptionTranslator(springExceptions(dataSource))
                .build();
    }

    @Bean
    Fetcher<OrderEntity> orderFetcher() {
        return OrderEntityFetcher.$.allScalarFields().customerId();
    }

    @Bean
    CrudStore<Product> products(JSqlClient sql) {
        return new JimmerProducts(sql);
    }

    @Bean
    CrudStore<Customer> customers(JSqlClient sql) {
        return new JimmerCustomers(sql);
    }

    @Bean
    CrudStore<Order> orders(JSqlClient sql) {
        return new JimmerOrders(sql);
    }

    @Bean
    EntityMapper entityMapper() {
        return entity -> switch (entity) {
            case ProductEntity p -> JimmerProducts.toDomain(p);
            case CustomerEntity c -> JimmerCustomers.toDomain(c);
            case OrderEntity o -> JimmerOrders.toDomain(o);
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

    private static ExceptionTranslator<SQLException> springExceptions(DataSource dataSource) {
        SQLExceptionTranslator spring = new SQLErrorCodeSQLExceptionTranslator(dataSource);
        return new ExceptionTranslator<>() {
            @Override
            public Exception translate(SQLException e, Args args) {
                DataAccessException translated = spring.translate("Jimmer", args.sql(), e);
                return translated != null ? translated : e;
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
