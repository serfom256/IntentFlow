package io.github.serfom256.intentflow.starter.autoconfigure;

import com.datastax.oss.driver.api.core.CqlSession;
import io.github.serfom256.intentflow.cassandra.CassandraQueryBuilder;
import io.github.serfom256.intentflow.core.catalog.NameResolver;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import io.github.serfom256.intentflow.core.pipeline.SqlBuilder;
import io.github.serfom256.intentflow.jdbc.JdbcQueryExecutor;
import io.github.serfom256.intentflow.jimmer.JimmerNameResolver;
import io.github.serfom256.intentflow.jimmer.JimmerQueryExecutor;
import io.github.serfom256.intentflow.jpa.JpaNameResolver;
import io.github.serfom256.intentflow.jpa.JpaQueryExecutor;
import io.github.serfom256.intentflow.openmapper.OpenMapperNameResolver;
import io.github.serfom256.intentflow.openmapper.OpenMapperQueryExecutor;
import io.github.serfom256.intentflow.springdata.cassandra.CassandraNameResolver;
import io.github.serfom256.intentflow.springdata.cassandra.CassandraQueryExecutor;
import io.github.serfom256.intentflow.springdata.jdbc.SpringDataJdbcNameResolver;
import io.github.serfom256.intentflow.springdata.jdbc.SpringDataJdbcQueryExecutor;
import io.github.serfom256.procedures.Procedures;
import jakarta.persistence.EntityManagerFactory;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.fetcher.Fetcher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.cassandra.core.convert.CassandraConverter;
import org.springframework.data.jdbc.core.convert.JdbcConverter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.orm.jpa.SharedEntityManagerCreator;

final class QueryExecutorConfigurations {

    private QueryExecutorConfigurations() {
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({ OpenMapperQueryExecutor.class, Procedures.class })
    static class OpenMapper {

        @Bean
        @ConditionalOnMissingBean(OpenMapperNameResolver.class)
        NameResolver intentFlowOpenMapperNameResolver() {
            return new OpenMapperNameResolver();
        }

        @Bean
        @ConditionalOnMissingBean(QueryExecutor.class)
        @ConditionalOnBean(Procedures.class)
        @ConditionalOnProperty(prefix = "intentflow", name = "executor", havingValue = "openmapper", matchIfMissing = true)
        QueryExecutor intentFlowOpenMapperQueryExecutor(Procedures procedures, ObjectProvider<JdbcClient> jdbcClient) {
            return new OpenMapperQueryExecutor(procedures, jdbcClient.getIfAvailable());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({ JpaQueryExecutor.class, EntityManagerFactory.class, SharedEntityManagerCreator.class })
    static class Jpa {

        @Bean
        @ConditionalOnMissingBean(JpaNameResolver.class)
        NameResolver intentFlowJpaNameResolver() {
            return new JpaNameResolver();
        }

        @Bean
        @ConditionalOnMissingBean(QueryExecutor.class)
        @ConditionalOnBean(EntityManagerFactory.class)
        @ConditionalOnProperty(prefix = "intentflow", name = "executor", havingValue = "jpa", matchIfMissing = true)
        QueryExecutor intentFlowJpaQueryExecutor(EntityManagerFactory entityManagerFactory) {
            return new JpaQueryExecutor(SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({ JimmerQueryExecutor.class, JSqlClient.class })
    static class Jimmer {

        @Bean
        @ConditionalOnMissingBean(JimmerNameResolver.class)
        NameResolver intentFlowJimmerNameResolver() {
            return new JimmerNameResolver();
        }

        @Bean
        @ConditionalOnMissingBean(QueryExecutor.class)
        @ConditionalOnBean(JSqlClient.class)
        @ConditionalOnProperty(prefix = "intentflow", name = "executor", havingValue = "jimmer", matchIfMissing = true)
        QueryExecutor intentFlowJimmerQueryExecutor(JSqlClient sqlClient, ObjectProvider<Fetcher<?>> fetchers) {
            return new JimmerQueryExecutor(sqlClient, fetchers.orderedStream().toList());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({ SpringDataJdbcQueryExecutor.class, JdbcConverter.class, JdbcClient.class })
    static class SpringDataJdbc {

        @Bean
        @ConditionalOnMissingBean(SpringDataJdbcNameResolver.class)
        @ConditionalOnBean(JdbcConverter.class)
        NameResolver intentFlowSpringDataJdbcNameResolver(JdbcConverter converter) {
            return new SpringDataJdbcNameResolver(converter.getMappingContext());
        }

        @Bean
        @ConditionalOnMissingBean(QueryExecutor.class)
        @ConditionalOnBean({ JdbcConverter.class, JdbcClient.class })
        @ConditionalOnProperty(prefix = "intentflow", name = "executor", havingValue = "spring-data-jdbc", matchIfMissing = true)
        QueryExecutor intentFlowSpringDataJdbcQueryExecutor(JdbcClient jdbcClient, JdbcConverter converter) {
            return new SpringDataJdbcQueryExecutor(jdbcClient, converter);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({ CassandraQueryExecutor.class, CqlSession.class, CassandraConverter.class })
    static class Cassandra {

        @Bean
        @ConditionalOnMissingBean(CassandraNameResolver.class)
        @ConditionalOnBean(CassandraConverter.class)
        NameResolver intentFlowCassandraNameResolver(CassandraConverter converter) {
            return new CassandraNameResolver(converter.getMappingContext());
        }

        @Configuration(proxyBeanMethods = false)
        @ConditionalOnBean({ CqlSession.class, CassandraConverter.class })
        @ConditionalOnMissingBean(QueryExecutor.class)
        @ConditionalOnProperty(prefix = "intentflow", name = "executor", havingValue = "cassandra", matchIfMissing = true)
        static class Executor {

            @Bean
            @ConditionalOnMissingBean(SqlBuilder.class)
            SqlBuilder intentFlowCassandraQueryBuilder(CqlSession session) {
                return new CassandraQueryBuilder(session);
            }

            @Bean
            QueryExecutor intentFlowCassandraQueryExecutor(CqlSession session, CassandraConverter converter,
                    IntentFlowProperties properties) {
                return new CassandraQueryExecutor(session, converter, properties.cassandra().maxScannedRows());
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(JdbcQueryExecutor.class)
    static class Jdbc {

        @Bean
        @ConditionalOnMissingBean(QueryExecutor.class)
        @ConditionalOnBean(JdbcClient.class)
        @ConditionalOnProperty(prefix = "intentflow", name = "executor", havingValue = "jdbc", matchIfMissing = true)
        QueryExecutor intentFlowJdbcQueryExecutor(JdbcClient jdbcClient) {
            return new JdbcQueryExecutor(jdbcClient);
        }
    }
}
