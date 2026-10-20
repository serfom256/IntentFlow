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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.jdbc.support.MetaDataAccessException;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.DatabaseMetaData;
import java.util.List;
import java.util.Locale;

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
                ? "No SQL dialect on the classpath: add intentflow-postgresql, intentflow-mysql, intentflow-h2 or another dialect"
                : "Several SQL dialects on the classpath " + available.stream().map(SqlDialect::name).toList()
                        + " and no DataSource to choose from: set intentflow.dialect");
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(JdbcUtils.class)
    @ConditionalOnBean(DataSource.class)
    static class Jdbc {

        private static final List<String> CONNECTION_DETAILS = List.of(
                "org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails",
                "org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails");

        @Bean
        @ConditionalOnMissingBean
        SqlDialect intentFlowSqlDialect(IntentFlowProperties properties, ObjectProvider<DataSource> dataSource,
                BeanFactory beanFactory) {
            ClassLoader classLoader = classLoader(beanFactory);
            if (StringUtils.hasText(properties.dialect())) {
                return SqlDialects.named(properties.dialect(), classLoader);
            }
            String url = jdbcUrl(beanFactory, classLoader);
            SqlDialect dialect = url == null ? null : dialectOf(url, classLoader);
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
                dialect = dialectOf(product, classLoader);
                if (dialect == null) {
                    throw new IllegalStateException("IntentFlow has no SQL dialect for " + product
                            + "; supported: PostgreSQL, MySQL, MariaDB, H2. Set intentflow.dialect "
                            + "or define a SqlDialect bean.");
                }
            }
            log.info("IntentFlow SQL dialect: {}", dialect.name());
            return dialect;
        }

        private static @Nullable String jdbcUrl(BeanFactory beanFactory, ClassLoader classLoader) {
            for (String name : CONNECTION_DETAILS) {
                if (!ClassUtils.isPresent(name, classLoader)) {
                    continue;
                }
                Class<?> type = ClassUtils.resolveClassName(name, classLoader);
                Object details = beanFactory.getBeanProvider(type).getIfUnique();
                if (details != null) {
                    try {
                        return (String) type.getMethod("getJdbcUrl").invoke(details);
                    } catch (ReflectiveOperationException e) {
                        log.debug("Could not read the JDBC URL from {}", name, e);
                    }
                }
            }
            return null;
        }

        static @Nullable SqlDialect dialectOf(String urlOrProduct, ClassLoader classLoader) {
            String value = urlOrProduct.toLowerCase(Locale.ROOT).replace("jdbc:tc:", "jdbc:");
            if (value.startsWith("jdbc:postgresql:") || value.equals("postgresql")) {
                return SqlDialects.named("postgresql", classLoader);
            }
            if (value.startsWith("jdbc:h2:") || value.equals("h2")) {
                return SqlDialects.named("h2", classLoader);
            }
            if (value.startsWith("jdbc:mysql:") || value.startsWith("jdbc:mariadb:")
                    || value.equals("mysql") || value.equals("mariadb")) {
                return SqlDialects.named("mysql", classLoader);
            }
            return null;
        }
    }
}
