package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.intentflow.core.catalog.CatalogOverrides;
import io.github.serfom256.intentflow.core.catalog.NameResolver;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceDescriptors;
import io.github.serfom256.intentflow.core.pipeline.AstValidator;
import io.github.serfom256.intentflow.core.pipeline.DslCache;
import io.github.serfom256.intentflow.core.pipeline.DslParser;
import io.github.serfom256.intentflow.core.pipeline.IntentPipeline;
import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import io.github.serfom256.intentflow.core.pipeline.SqlBuilder;
import io.github.serfom256.intentflow.core.pipeline.SqlDialect;
import io.github.serfom256.intentflow.core.pipeline.TextNormalizer;
import io.github.serfom256.intentflow.core.pipeline.UriBuilder;
import io.github.serfom256.intentflow.core.pipeline.factories.AstValidators;
import io.github.serfom256.intentflow.core.pipeline.factories.DslCaches;
import io.github.serfom256.intentflow.core.pipeline.factories.DslParsers;
import io.github.serfom256.intentflow.core.pipeline.factories.IntentPipelines;
import io.github.serfom256.intentflow.core.pipeline.factories.LlmClients;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.core.pipeline.factories.TextNormalizers;
import io.github.serfom256.intentflow.core.pipeline.factories.UriBuilders;
import io.github.serfom256.intentflow.core.schema.SchemaFacts;
import io.github.serfom256.intentflow.core.sql.Optimization;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

@Slf4j
@AutoConfiguration(afterName = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration",
        "org.springframework.boot.data.jdbc.autoconfigure.DataJdbcRepositoriesAutoConfiguration",
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
        "io.github.serfom256.config.OpenMapperConfig",
        "org.springframework.boot.cassandra.autoconfigure.CassandraAutoConfiguration",
        "org.springframework.boot.data.cassandra.autoconfigure.DataCassandraAutoConfiguration",
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "org.springframework.boot.autoconfigure.jdbc.JdbcClientAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.jdbc.JdbcRepositoriesAutoConfiguration",
        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
        "org.springframework.boot.autoconfigure.cassandra.CassandraAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.cassandra.CassandraDataAutoConfiguration" })
@ConditionalOnProperty(prefix = "intentflow", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(IntentFlowProperties.class)
@Import({ SqlDialectConfigurations.Jdbc.class, QueryExecutorConfigurations.OpenMapper.class,
        QueryExecutorConfigurations.Jpa.class,
        QueryExecutorConfigurations.Jimmer.class, QueryExecutorConfigurations.SpringDataJdbc.class,
        QueryExecutorConfigurations.Cassandra.class, QueryExecutorConfigurations.Jdbc.class })
public class IntentFlowAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ServiceCatalog intentFlowServiceCatalog(IntentFlowProperties properties,
            BeanFactory beanFactory,
            ObjectProvider<ServiceDescriptor> descriptorBeans,
            ObjectProvider<NameResolver> nameResolvers,
            ObjectProvider<CatalogOverrides> overrides) {
        List<String> packages = properties.catalog().packages().isEmpty() && AutoConfigurationPackages.has(beanFactory)
                ? AutoConfigurationPackages.get(beanFactory)
                : properties.catalog().packages();

        List<ServiceDescriptor> descriptors = new ArrayList<>();
        ClassLoader classLoader = beanFactory instanceof ConfigurableBeanFactory cbf && cbf.getBeanClassLoader() != null
                ? cbf.getBeanClassLoader()
                : ClassUtils.getDefaultClassLoader();
        List<NameResolver> resolvers = nameResolvers.orderedStream().toList();
        for (Class<?> type : new IntentServiceScanner(classLoader).scan(packages)) {
            descriptors.add(ServiceDescriptors.fromAnnotated(type, resolvers));
        }
        descriptorBeans.orderedStream().forEach(descriptors::add);

        if (descriptors.isEmpty()) {
            throw new IllegalStateException("No IntentFlow services found. Annotate model classes with "
                    + "@IntentService under " + packages + " (or set intentflow.catalog.packages), "
                    + "or declare ServiceDescriptor beans.");
        }
        ServiceCatalog catalog = ServiceCatalogs.of(descriptors);
        log.info("IntentFlow catalog: {}", descriptors.stream().map(d -> d.name().value()).toList());
        CatalogOverrides runtimeOverrides = overrides.getIfAvailable();
        return runtimeOverrides == null ? catalog : ServiceCatalogs.withOverrides(catalog, runtimeOverrides);
    }

    @Bean
    @ConditionalOnMissingBean
    LlmClient intentFlowLlmClient(IntentFlowProperties properties, ObjectProvider<Clock> clock) {
        IntentFlowProperties.Llm llm = properties.llm();
        if (!StringUtils.hasText(llm.model())) {
            throw new IllegalStateException(
                    "Set intentflow.llm.model (and intentflow.llm.base-url) or define an LlmClient bean.");
        }
        return LlmClients.openAiCompatible(llm.baseUrl(), llm.model(), llm.apiKey(), llm.timeout(),
                clock.getIfAvailable(Clock::systemDefaultZone));
    }

    @Bean
    @ConditionalOnMissingBean
    TextNormalizer intentFlowTextNormalizer() {
        return TextNormalizers.create();
    }

    @Bean
    @ConditionalOnMissingBean
    DslCache intentFlowDslCache(IntentFlowProperties properties) {
        return DslCaches.create(properties.cache().ttl(), properties.cache().maxSize());
    }

    @Bean
    @ConditionalOnMissingBean
    DslParser intentFlowDslParser() {
        return DslParsers.create();
    }

    @Bean
    @ConditionalOnMissingBean
    AstValidator intentFlowAstValidator(IntentFlowProperties properties) {
        return AstValidators.create(properties.maxSkip());
    }

    @Bean
    @ConditionalOnMissingBean
    SqlBuilder intentFlowSqlBuilder(ObjectProvider<SqlDialect> dialect, IntentFlowProperties properties,
            SchemaFacts facts, BeanFactory beanFactory) {
        Set<Optimization> optimizations = EnumSet.allOf(Optimization.class);
        properties.sql().disabledOptimizations().forEach(id -> optimizations.remove(Optimization.byId(id)));
        Function<SqlDialect, SqlBuilder> factory = d -> SqlBuilders.create(d, facts, optimizations);
        SqlDialect detected = dialect.getIfAvailable();
        if (detected != null) {
            return factory.apply(detected);
        }
        ClassLoader classLoader = SqlDialectConfigurations.classLoader(beanFactory);
        return new LazySqlBuilder(() -> SqlDialectConfigurations.fromPropertyOrClasspath(properties, classLoader),
                factory);
    }

    @Bean
    @ConditionalOnMissingBean
    SchemaFacts intentFlowSchemaFacts(ServiceCatalog catalog, ObjectProvider<NameResolver> nameResolvers,
            ObjectProvider<DataSource> dataSource, IntentFlowProperties properties) {
        return SchemaFactsFactory.create(catalog, nameResolvers.orderedStream().toList(), dataSource.getIfUnique(),
                properties.schema().introspect());
    }

    @Bean
    @ConditionalOnMissingBean
    UriBuilder intentFlowUriBuilder() {
        return UriBuilders.create();
    }

    @Bean
    @ConditionalOnMissingBean
    IntentPipeline intentFlowPipeline(TextNormalizer normalizer, DslCache cache, LlmClient llm,
            DslParser parser, AstValidator validator, SqlBuilder sqlBuilder,
            ObjectProvider<QueryExecutor> executor, UriBuilder uriBuilder,
            ServiceCatalog catalog) {
        QueryExecutor queryExecutor = executor.getIfAvailable();
        if (queryExecutor == null) {
            throw new IllegalStateException("No QueryExecutor: configure a DataSource (spring.datasource.*) "
                    + "or Cassandra (spring.cassandra.*) and add an executor module (intentflow-jdbc, "
                    + "intentflow-jpa, intentflow-jimmer, intentflow-openmapper, intentflow-spring-data-jdbc, "
                    + "intentflow-spring-data-cassandra), or define a QueryExecutor bean.");
        }
        return IntentPipelines.create(normalizer, cache, llm, parser, validator, sqlBuilder,
                queryExecutor, uriBuilder, catalog);
    }

}
