package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.intentflow.core.pipeline.SqlDialect;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlDialects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jdbc.DatabaseDriver;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.jdbc.support.MetaDataAccessException;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.DatabaseMetaData;
import java.util.List;

@Slf4j
final class SqlDialectConfigurations {

    private SqlDialectConfigurations() {
    }

    static ClassLoader classLoader(BeanFactory beanFactory) {
        return beanFactory instanceof ConfigurableBeanFactory cbf && cbf.getBeanClassLoader() != null
                ? cbf.getBeanClassLoader()
                : ClassUtils.getDefaultClassLoader();
    }

    static SqlDialect fromPropertyOrClasspath(IntentFlowProperties properties, ClassLoader classLoader) {
        if (StringUtils.hasText(properties.dialect())) {
            return SqlDialects.named(properties.dialect(), classLoader);
        }
        List<SqlDialect> available = SqlDialects.available(classLoader);
        if (available.size() == 1) {
            return available.getFirst();
        }
        throw new IllegalStateException(available.isEmpty()
                ? "No SQL dialect on the classpath: add intentflow-postgresql, intentflow-mysql or another dialect"
                : "Several SQL dialects on the classpath " + available.stream().map(SqlDialect::name).toList()
                        + " and no DataSource to choose from: set intentflow.dialect");
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({DatabaseDriver.class, JdbcUtils.class})
    @ConditionalOnBean(DataSource.class)
    static class Jdbc {

        @Bean
        @ConditionalOnMissingBean
        SqlDialect intentFlowSqlDialect(IntentFlowProperties properties,
                                        ObjectProvider<JdbcConnectionDetails> connectionDetails,
                                        ObjectProvider<DataSource> dataSource,
                                        BeanFactory beanFactory) {
            ClassLoader classLoader = classLoader(beanFactory);
            if (StringUtils.hasText(properties.dialect())) {
                return SqlDialects.named(properties.dialect(), classLoader);
            }
            JdbcConnectionDetails details = connectionDetails.getIfUnique();
            SqlDialect dialect = details == null ? null
                    : dialectOf(DatabaseDriver.fromJdbcUrl(details.getJdbcUrl()), classLoader);
            if (dialect == null) {
                DataSource ds = dataSource.getIfUnique();
                if (ds == null) {
                    return fromPropertyOrClasspath(properties, classLoader);
                }
                String product;
                try {
                    product = JdbcUtils.extractDatabaseMetaData(ds, DatabaseMetaData::getDatabaseProductName);
                } catch (MetaDataAccessException e) {
                    throw new IllegalStateException(
                            "Could not detect the SQL dialect from the DataSource; set intentflow.dialect", e);
                }
                dialect = dialectOf(DatabaseDriver.fromProductName(product), classLoader);
                if (dialect == null) {
                    throw new IllegalStateException("IntentFlow has no SQL dialect for " + product
                            + "; supported: PostgreSQL, MySQL, MariaDB, H2. Set intentflow.dialect "
                            + "or define a SqlDialect bean.");
                }
            }
            log.info("IntentFlow SQL dialect: {}", dialect.name());
            return dialect;
        }

        private static @Nullable SqlDialect dialectOf(DatabaseDriver driver, ClassLoader classLoader) {
            return switch (driver) {
                case POSTGRESQL, H2 -> SqlDialects.named("postgresql", classLoader);
                case MYSQL, MARIADB -> SqlDialects.named("mysql", classLoader);
                default -> null;
            };
        }
    }
}
