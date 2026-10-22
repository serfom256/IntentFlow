package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.intentflow.core.api.factories.IntentContexts;
import io.github.serfom256.intentflow.core.catalog.CatalogOverrides;
import io.github.serfom256.intentflow.core.catalog.NameResolver;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceDescriptors;
import io.github.serfom256.intentflow.core.pipeline.AccessPolicy;
import io.github.serfom256.intentflow.core.pipeline.AstValidator;
import io.github.serfom256.intentflow.core.pipeline.AuditLog;
import io.github.serfom256.intentflow.core.pipeline.DslCache;
import io.github.serfom256.intentflow.core.pipeline.DslParser;
import io.github.serfom256.intentflow.core.pipeline.IntentPipeline;
import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import io.github.serfom256.intentflow.core.pipeline.LlmRateLimiter;
import io.github.serfom256.intentflow.core.pipeline.PipelineListener;
import io.github.serfom256.intentflow.core.pipeline.PipelineTracer;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import io.github.serfom256.intentflow.core.pipeline.SqlBuilder;
import io.github.serfom256.intentflow.core.pipeline.SqlDialect;
import io.github.serfom256.intentflow.core.pipeline.TextNormalizer;
import io.github.serfom256.intentflow.core.pipeline.UriBuilder;
import io.github.serfom256.intentflow.core.pipeline.factories.AstValidators;
import io.github.serfom256.intentflow.core.pipeline.factories.AuditLogs;
import io.github.serfom256.intentflow.core.pipeline.factories.DslCaches;
import io.github.serfom256.intentflow.core.pipeline.factories.DslParsers;
import io.github.serfom256.intentflow.core.pipeline.factories.IntentPipelines;
import io.github.serfom256.intentflow.core.pipeline.factories.LlmClients;
import io.github.serfom256.intentflow.core.pipeline.factories.LlmRateLimiters;
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
        QueryExecutorConfigurations.Cassandra.class, QueryExecutorConfigurations.Jdbc.class,
        PipelineObservers.SpringSecurity.class, PipelineObservers.Micrometer.class,
        PipelineObservers.Observations.class, LlmHealthBoot4.class, LlmHealthBoot3.class,
        Resilience4jLlmClient.Wiring.class, OpenAiProviders.OpenAi.class, OpenAiProviders.AzureOpenAi.class,
        OpenAiProviders.GigaChat.class, AnthropicProvider.class, VertexProvider.class, BedrockProvider.class,
        TransactionalQueryExecutor.Wiring.class })
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
    LlmClient intentFlowLlmClient(IntentFlowProperties properties, ObjectProvider<LlmClientFactory> factories,
            ObjectProvider<LlmCircuitBreaker> breakers) {
        IntentFlowProperties.Llm llm = properties.llm();
        if (!StringUtils.hasText(llm.model())) {
            throw new IllegalStateException(
                    "Set intentflow.llm.model (and intentflow.llm.base-url) or define an LlmClient bean.");
        }
        LlmClientFactory factory = factories.getIfAvailable();
        if (factory == null) {
            List<String> present = LlmProviders.present(getClass().getClassLoader());
            if (!StringUtils.hasText(llm.provider()) && present.size() > 1) {
                throw new IllegalStateException("Several LLM provider modules are on the classpath " + present
                        + ": set intentflow.llm.provider to one of them.");
            }
            throw new IllegalStateException("No LLM provider for intentflow.llm.provider="
                    + (StringUtils.hasText(llm.provider()) ? llm.provider() : "openai") + ": add a module from "
                    + "intentflow-providers (intentflow-openai for OpenAI-compatible APIs) or define an LlmClient bean.");
        }
        LlmCircuitBreaker breaker = breakers.getIfAvailable(() -> (client, name) -> builtInBreaker(client, llm));
        LlmClient primary = guarded(factory.create(llm.baseUrl(), llm.model(), llm.apiKey()), llm, breaker,
                "intentflow-llm");
        IntentFlowProperties.Llm.Fallback fallback = llm.fallback();
        if (!StringUtils.hasText(fallback.model())) {
            return primary;
        }
        LlmClient secondary = factory.create(fallback.baseUrl() != null ? fallback.baseUrl() : llm.baseUrl(),
                fallback.model(), fallback.apiKey() != null ? fallback.apiKey() : llm.apiKey());
        return LlmClients.withFallback(primary, guarded(secondary, llm, breaker, "intentflow-llm-fallback"));
    }

    private static LlmClient guarded(LlmClient client, IntentFlowProperties.Llm llm, LlmCircuitBreaker breaker,
            String name) {
        LlmClient guarded = client;
        IntentFlowProperties.Llm.Concurrency concurrency = llm.concurrency();
        if (concurrency.maxCalls() > 0) {
            guarded = LlmClients.limited(guarded, concurrency.maxCalls(), concurrency.maxWait());
        }
        return llm.circuitBreaker().failureThreshold() > 0 ? breaker.wrap(guarded, name) : guarded;
    }

    static LlmClient builtInBreaker(LlmClient client, IntentFlowProperties.Llm llm) {
        return LlmClients.circuitBreaker(client, llm.circuitBreaker().failureThreshold(),
                llm.circuitBreaker().openFor());
    }

    @Bean
    @ConditionalOnMissingBean
    AuditLog intentFlowAuditLog(IntentFlowProperties properties) {
        return properties.audit().enabled() ? AuditLogs.slf4j() : AuditLog.NONE;
    }

    @Bean
    @ConditionalOnMissingBean
    LlmRateLimiter intentFlowLlmRateLimiter(IntentFlowProperties properties) {
        IntentFlowProperties.Llm.RateLimit limit = properties.llm().rateLimit();
        if (limit.questions() <= 0) {
            return LlmRateLimiter.NONE;
        }
        return limit.per() == IntentFlowProperties.Llm.RateLimit.Per.TENANT
                ? LlmRateLimiters.perTenant(limit.questions(), limit.period())
                : LlmRateLimiters.perPrincipal(limit.questions(), limit.period());
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
        return AstValidators.create(properties.maxSkip(), properties.maxInValues());
    }

    @Bean
    @ConditionalOnMissingBean
    SqlBuilder intentFlowSqlBuilder(ObjectProvider<SqlDialect> dialect, IntentFlowProperties properties,
            SchemaFacts facts, BeanFactory beanFactory) {
        Set<Optimization> optimizations = EnumSet.allOf(Optimization.class);
        properties.sql().disabledOptimizations().forEach(id -> optimizations.remove(Optimization.byId(id)));
        Function<SqlDialect, SqlBuilder> factory = d -> SqlBuilders.create(d, facts, optimizations, properties.zone());
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
            ServiceCatalog catalog, IntentFlowProperties properties, ObjectProvider<AccessPolicy> policy,
            ObjectProvider<IntentContextProvider> callers, ObjectProvider<PipelineListener> listeners,
            ObjectProvider<PipelineTracer> tracer, ObjectProvider<ExecutorWrapper> wrapper,
            LlmRateLimiter rateLimiter, AuditLog audit) {
        QueryExecutor queryExecutor = executor.getIfAvailable();
        if (queryExecutor == null) {
            throw new IllegalStateException("No QueryExecutor: configure a DataSource (spring.datasource.*) "
                    + "or Cassandra (spring.cassandra.*) and add an executor module (intentflow-jdbc, "
                    + "intentflow-jpa, intentflow-jimmer, intentflow-openmapper, intentflow-spring-data-jdbc, "
                    + "intentflow-spring-data-cassandra), or define a QueryExecutor bean.");
        }
        ExecutorWrapper wrap = wrapper.getIfAvailable();
        if (wrap != null) {
            queryExecutor = wrap.apply(queryExecutor);
        }
        IntentContextProvider caller = callers.getIfAvailable(() -> IntentContexts::anonymous);
        List<PipelineListener> observers = listeners.orderedStream().toList();
        return IntentPipelines.builder()
                .llm(llm)
                .sqlBuilder(sqlBuilder)
                .executor(queryExecutor)
                .catalog(catalog)
                .normalizer(normalizer)
                .cache(cache)
                .parser(parser)
                .validator(validator)
                .uriBuilder(uriBuilder)
                .maxRepairs(properties.llm().maxRepairs())
                .accessPolicy(policy.getIfAvailable(() -> AccessPolicy.ALLOW_ALL))
                .defaultCaller(caller::current)
                .listener(new CompositeListener(observers))
                .tracer(tracer.getIfAvailable(() -> PipelineTracer.NONE))
                .rateLimiter(rateLimiter)
                .audit(audit)
                .logValues(properties.logging().values())
                .visibleFieldsOnly(properties.results().visibleFieldsOnly())
                .build();
    }

}
