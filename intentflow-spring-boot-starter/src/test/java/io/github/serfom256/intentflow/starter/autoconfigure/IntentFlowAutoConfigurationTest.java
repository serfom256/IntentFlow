package io.github.serfom256.intentflow.starter.autoconfigure;

import com.datastax.oss.driver.api.core.CqlSession;
import io.github.serfom256.intentflow.cassandra.CassandraQueryBuilder;
import io.github.serfom256.intentflow.core.api.IntentResponse;
import io.github.serfom256.intentflow.core.api.factories.IntentRequests;
import io.github.serfom256.intentflow.core.api.factories.UserTexts;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.FieldType;
import io.github.serfom256.intentflow.core.catalog.NameResolver;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.catalog.factories.ColumnNames;
import io.github.serfom256.intentflow.core.catalog.factories.FieldMappings;
import io.github.serfom256.intentflow.core.catalog.factories.LogicalFieldNames;
import io.github.serfom256.intentflow.core.catalog.factories.MaxTakes;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceDescriptors;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceNames;
import io.github.serfom256.intentflow.core.catalog.factories.TableNames;
import io.github.serfom256.intentflow.core.catalog.factories.UriTemplates;
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
import io.github.serfom256.intentflow.core.pipeline.ValidatedQuery;
import io.github.serfom256.intentflow.core.pipeline.factories.DslParsers;
import io.github.serfom256.intentflow.core.pipeline.impl.OpenAiCompatibleLlmClient;
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
import io.github.serfom256.intentflow.starter.autoconfigure.fixtures.Book;
import io.github.serfom256.procedures.Procedures;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.runtime.JSqlClientImplementor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.cassandra.core.convert.CassandraConverter;
import org.springframework.data.cassandra.core.convert.MappingCassandraConverter;
import org.springframework.data.jdbc.core.convert.JdbcConverter;
import org.springframework.data.jdbc.core.convert.MappingJdbcConverter;
import org.springframework.data.jdbc.core.mapping.JdbcMappingContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class IntentFlowAutoConfigurationTest {

    private static final String FIXTURES = "io.github.serfom256.intentflow.starter.autoconfigure.fixtures";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    BootAutoConfigurations.DATA_SOURCE,
                    BootAutoConfigurations.JDBC_TEMPLATE,
                    BootAutoConfigurations.JDBC_CLIENT,
                    IntentFlowAutoConfiguration.class))
            .withPropertyValues(
                    "spring.datasource.url=jdbc:h2:mem:af;DB_CLOSE_DELAY=-1",
                    "intentflow.llm.model=test-model",
                    "intentflow.catalog.packages=" + FIXTURES);

    private static List<String> serviceNames(ServiceCatalog catalog) {
        return catalog.names().stream().map(ServiceName::value).sorted().toList();
    }

    @Test
    void wiresTheWholePipeline() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).hasSingleBean(IntentPipeline.class)
                    .hasSingleBean(ServiceCatalog.class)
                    .hasSingleBean(TextNormalizer.class)
                    .hasSingleBean(DslCache.class)
                    .hasSingleBean(DslParser.class)
                    .hasSingleBean(AstValidator.class)
                    .hasSingleBean(SqlBuilder.class)
                    .hasSingleBean(UriBuilder.class)
                    .hasSingleBean(QueryExecutor.class);
            assertThat(ctx.getBean(LlmClient.class)).isInstanceOf(OpenAiCompatibleLlmClient.class);
        });
    }

    @Test
    void scansAnnotatedClassesIncludingNestedOnesAndIgnoresOthers() {
        runner.run(ctx -> {
            ServiceCatalog catalog = ctx.getBean(ServiceCatalog.class);
            assertThat(serviceNames(catalog)).containsExactly("authors", "books");
            assertThat(catalog.get(ServiceNames.of("books")).fields())
                    .extracting(f -> f.type()).containsExactly(FieldType.NUMBER, FieldType.TEXT, FieldType.NUMBER);
        });
    }

    @Test
    void scansMultipleConfiguredPackages() {
        runner.withPropertyValues("intentflow.catalog.packages=" + FIXTURES
                + ",io.github.serfom256.intentflow.starter.autoconfigure.other")
                .run(ctx -> assertThat(serviceNames(ctx.getBean(ServiceCatalog.class)))
                        .containsExactly("authors", "books", "magazines"));
    }

    @Test
    void defaultsToAutoConfigurationPackages() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(IntentFlowAutoConfiguration.class))
                .withUserConfiguration(OtherPackageApp.class, StubExecutor.class)
                .withPropertyValues("intentflow.llm.model=m")
                .run(ctx -> assertThat(serviceNames(ctx.getBean(ServiceCatalog.class)))
                        .containsExactly("magazines"));
    }

    @Test
    void programmaticDescriptorBeansAreMergedWithAnnotatedOnes() {
        runner.withUserConfiguration(ManualDescriptor.class)
                .run(ctx -> assertThat(serviceNames(ctx.getBean(ServiceCatalog.class)))
                        .containsExactly("authors", "books", "orders"));
    }

    @Test
    void duplicateServiceNamesFailStartup() {
        runner.withBean("dup", ServiceDescriptor.class, () -> ServiceDescriptors.fromAnnotated(Book.class))
                .run(ctx -> assertThat(ctx).getFailure().rootCause()
                        .hasMessageContaining("duplicate service: books"));
    }

    @Test
    void userCatalogReplacesScanning() {
        runner.withBean(ServiceCatalog.class, () -> ServiceCatalogs.fromAnnotated(Book.Author.class))
                .run(ctx -> assertThat(serviceNames(ctx.getBean(ServiceCatalog.class))).containsExactly("authors"));
    }

    @Test
    void userLlmClientMeansNoModelIsRequired() {
        LlmClient custom = (text, catalog) -> DslSources.of("books");
        runner.withPropertyValues("intentflow.llm.model=")
                .withBean(LlmClient.class, () -> custom)
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx.getBean(LlmClient.class)).isSameAs(custom);
                });
    }

    @Test
    void missingModelFailsFastWithAHint() {
        runner.withPropertyValues("intentflow.llm.model=")
                .run(ctx -> assertThat(ctx).getFailure().rootCause()
                        .hasMessageContaining("Set intentflow.llm.model"));
    }

    @Test
    void noServicesFailsFastWithAHint() {
        runner.withPropertyValues("intentflow.catalog.packages=io.github.serfom256.nothing.here")
                .run(ctx -> assertThat(ctx).getFailure().rootCause()
                        .hasMessageContaining("No IntentFlow services found")
                        .hasMessageContaining("io.github.serfom256.nothing.here"));
    }

    @Test
    void noDataSourceAndNoExecutorFailsFastWithAHint() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(IntentFlowAutoConfiguration.class))
                .withPropertyValues("intentflow.llm.model=m", "intentflow.catalog.packages=" + FIXTURES)
                .run(ctx -> assertThat(ctx).getFailure().rootCause()
                        .hasMessageContaining("No QueryExecutor"));
    }

    @Test
    void customExecutorWorksWithoutJdbc() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(IntentFlowAutoConfiguration.class))
                .withUserConfiguration(StubExecutor.class)
                .withPropertyValues("intentflow.llm.model=m", "intentflow.catalog.packages=" + FIXTURES)
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed().doesNotHaveBean(JdbcClient.class);
                    assertThat(ctx.getBean(QueryExecutor.class)).isSameAs(ctx.getBean(StubExecutor.class).executor);
                });
    }

    @Test
    void canBeDisabled() {
        runner.withPropertyValues("intentflow.enabled=false")
                .run(ctx -> assertThat(ctx).hasNotFailed()
                        .doesNotHaveBean(IntentPipeline.class)
                        .doesNotHaveBean(ServiceCatalog.class));
    }

    @Test
    void bindsProperties() {
        runner.withPropertyValues(
                "intentflow.llm.base-url=http://ollama:11434/v1",
                "intentflow.llm.api-key=secret",
                "intentflow.llm.timeout=5s",
                "intentflow.cache.ttl=1h",
                "intentflow.cache.max-size=42")
                .run(ctx -> {
                    IntentFlowProperties p = ctx.getBean(IntentFlowProperties.class);
                    assertThat(p.llm().baseUrl()).hasToString("http://ollama:11434/v1");
                    assertThat(p.llm().apiKey()).isEqualTo("secret");
                    assertThat(p.llm().timeout()).isEqualTo(Duration.ofSeconds(5));
                    assertThat(p.cache().ttl()).isEqualTo(Duration.ofHours(1));
                    assertThat(p.cache().maxSize()).isEqualTo(42);
                });
    }

    @Test
    void defaultProperties() {
        runner.run(ctx -> {
            IntentFlowProperties p = ctx.getBean(IntentFlowProperties.class);
            assertThat(p.enabled()).isTrue();
            assertThat(p.llm().baseUrl()).hasToString("http://localhost:1234/v1");
            assertThat(p.llm().apiKey()).isEmpty();
            assertThat(p.llm().timeout()).isEqualTo(Duration.ofSeconds(60));
            assertThat(p.cache().ttl()).isEqualTo(Duration.ofMinutes(10));
            assertThat(p.cache().maxSize()).isEqualTo(10_000);
        });
    }

    @Test
    void userComponentsWinOverDefaults() {
        SqlBuilder customSql = validated -> {
            throw new UnsupportedOperationException();
        };
        runner.withBean(SqlBuilder.class, () -> customSql)
                .run(ctx -> assertThat(ctx.getBean(SqlBuilder.class)).isSameAs(customSql));
    }

    @Test
    void autoConfiguredPipelineRunsEndToEndOnH2() {
        runner.withBean(LlmClient.class, () -> (text, catalog) -> DslSources.of("books | filter title == \"DUNE\""))
                .run(ctx -> {
                    JdbcClient jdbc = ctx.getBean(JdbcClient.class);
                    jdbc.sql("CREATE TABLE \"book\" (\"id\" BIGINT, \"title\" VARCHAR(100), \"price\" DECIMAL(6,2))")
                            .update();
                    jdbc.sql("INSERT INTO \"book\" VALUES (7, 'Dune', 9.99), (8, 'Emma', 5.00)").update();

                    IntentResponse<Book> response = ctx.getBean(IntentPipeline.class)
                            .resolve(IntentRequests.of("dune please"), Book.class);

                    assertThat(response.uri().value()).isEqualTo("/books/7");
                    assertThat(response.items()).containsExactly(new Book(7, "Dune", new BigDecimal("9.99")));
                });
    }

    @Test
    void dialectComesFromTheJdbcUrl() {
        runner.run(ctx -> assertThat(ctx.getBean(SqlDialect.class).name()).isEqualTo("postgresql"));
        runner.withPropertyValues("spring.datasource.url=jdbc:mysql://localhost:3306/shop")
                .run(ctx -> assertThat(ctx.getBean(SqlDialect.class).name()).isEqualTo("mysql"));
        runner.withPropertyValues("spring.datasource.url=jdbc:mariadb://localhost:3306/shop",
                "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver")
                .run(ctx -> assertThat(ctx.getBean(SqlDialect.class).name()).isEqualTo("mysql"));
    }

    @Test
    void dialectComesFromDatabaseMetadataWithoutConnectionDetails() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(IntentFlowAutoConfiguration.class))
                .withUserConfiguration(StubExecutor.class)
                .withBean(DataSource.class, () -> new DriverManagerDataSource("jdbc:h2:mem:meta"))
                .withPropertyValues("intentflow.llm.model=m", "intentflow.catalog.packages=" + FIXTURES)
                .run(ctx -> assertThat(ctx.getBean(SqlDialect.class).name()).isEqualTo("postgresql"));
    }

    @Test
    void dialectPropertyWinsOverDetection() {
        runner.withPropertyValues("intentflow.dialect=MySQL")
                .run(ctx -> assertThat(ctx.getBean(SqlDialect.class).name()).isEqualTo("mysql"));
    }

    @Test
    void unknownDialectPropertyFailsStartup() {
        runner.withPropertyValues("intentflow.dialect=oracle")
                .run(ctx -> assertThat(ctx).getFailure().rootCause()
                        .hasMessageContaining("Unknown SQL dialect 'oracle'")
                        .hasMessageContaining("intentflow-oracle"));
    }

    @Test
    void withoutADataSourceTheDialectComesFromThePropertyWhenAQueryIsBuilt() {
        noDataSource().withPropertyValues("intentflow.dialect=mysql").run(ctx -> {
            assertThat(ctx).hasNotFailed().doesNotHaveBean(SqlDialect.class);
            assertThat(buildBooks(ctx).sql()).startsWith("SELECT * FROM `book`");
        });
    }

    @Test
    void withoutADataSourceSeveralDialectsNeedTheProperty() {
        noDataSource().run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThatThrownBy(() -> buildBooks(ctx)).hasMessageContaining("set intentflow.dialect");
        });
    }

    private ApplicationContextRunner noDataSource() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(IntentFlowAutoConfiguration.class))
                .withUserConfiguration(StubExecutor.class)
                .withPropertyValues("intentflow.llm.model=m", "intentflow.catalog.packages=" + FIXTURES);
    }

    private static BoundQuery buildBooks(ApplicationContext ctx) {
        ValidatedQuery query = ctx.getBean(AstValidator.class)
                .validate(DslParsers.create().parse(DslSources.of("books")), ctx.getBean(ServiceCatalog.class));
        return ctx.getBean(SqlBuilder.class).build(query);
    }

    @Test
    void jdbcExecutorByDefault() {
        runner.run(ctx -> assertThat(ctx.getBean(QueryExecutor.class)).isInstanceOf(JdbcQueryExecutor.class));
    }

    @Test
    void openMapperWinsWhenItsProceduresBeanIsPresent() {
        runner.withBean(Procedures.class, () -> mock(Procedures.class))
                .run(ctx -> {
                    assertThat(ctx.getBean(QueryExecutor.class)).isInstanceOf(OpenMapperQueryExecutor.class);
                    assertThat(ctx.getBeansOfType(NameResolver.class).values())
                            .hasAtLeastOneElementOfType(OpenMapperNameResolver.class);
                });
    }

    @Test
    void jpaWinsWhenAnEntityManagerFactoryIsPresent() {
        runner.withConfiguration(AutoConfigurations.of(BootAutoConfigurations.HIBERNATE_JPA))
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx.getBean(QueryExecutor.class)).isInstanceOf(JpaQueryExecutor.class);
                    assertThat(ctx.getBeansOfType(NameResolver.class).values())
                            .hasAtLeastOneElementOfType(JpaNameResolver.class);
                });
    }

    @Test
    void executorPropertyForcesTheChoice() {
        runner.withConfiguration(AutoConfigurations.of(BootAutoConfigurations.HIBERNATE_JPA))
                .withBean(Procedures.class, () -> mock(Procedures.class))
                .withPropertyValues("intentflow.executor=jdbc")
                .run(ctx -> assertThat(ctx.getBean(QueryExecutor.class)).isInstanceOf(JdbcQueryExecutor.class));
    }

    @Test
    void cassandraRunsQueriesWhenASessionAndConverterExist() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(IntentFlowAutoConfiguration.class))
                .withBean(CqlSession.class, () -> mock(CqlSession.class))
                .withBean(CassandraConverter.class, MappingCassandraConverter::new)
                .withPropertyValues("intentflow.llm.model=m", "intentflow.catalog.packages=" + FIXTURES,
                        "intentflow.cassandra.max-scanned-rows=500")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx.getBean(QueryExecutor.class)).isInstanceOf(CassandraQueryExecutor.class);
                    assertThat(ctx.getBean(SqlBuilder.class)).isInstanceOf(CassandraQueryBuilder.class);
                    assertThat(ctx.getBeansOfType(NameResolver.class).values())
                            .hasAtLeastOneElementOfType(CassandraNameResolver.class);
                    assertThat(ctx.getBean(IntentFlowProperties.class).cassandra().maxScannedRows()).isEqualTo(500);
                });
    }

    @Test
    void cassandraComesAfterJpaButBeforeJdbc() {
        runner.withBean(CqlSession.class, () -> mock(CqlSession.class))
                .withBean(CassandraConverter.class, MappingCassandraConverter::new)
                .run(ctx -> assertThat(ctx.getBean(QueryExecutor.class)).isInstanceOf(CassandraQueryExecutor.class));
        runner.withConfiguration(AutoConfigurations.of(BootAutoConfigurations.HIBERNATE_JPA))
                .withBean(CqlSession.class, () -> mock(CqlSession.class))
                .withBean(CassandraConverter.class, MappingCassandraConverter::new)
                .run(ctx -> {
                    assertThat(ctx.getBean(QueryExecutor.class)).isInstanceOf(JpaQueryExecutor.class);
                    assertThat(ctx.getBean(SqlBuilder.class)).isNotInstanceOf(CassandraQueryBuilder.class);
                });
    }

    @Test
    void executorPropertyCanPickJdbcOverCassandra() {
        runner.withBean(CqlSession.class, () -> mock(CqlSession.class))
                .withBean(CassandraConverter.class, MappingCassandraConverter::new)
                .withPropertyValues("intentflow.executor=jdbc")
                .run(ctx -> {
                    assertThat(ctx.getBean(QueryExecutor.class)).isInstanceOf(JdbcQueryExecutor.class);
                    assertThat(ctx.getBean(SqlBuilder.class)).isNotInstanceOf(CassandraQueryBuilder.class);
                });
    }

    @Test
    void jimmerRunsQueriesWhenAJSqlClientExists() {
        runner.withBean(JSqlClient.class, () -> mock(JSqlClientImplementor.class))
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx.getBean(QueryExecutor.class)).isInstanceOf(JimmerQueryExecutor.class);
                    assertThat(ctx.getBeansOfType(NameResolver.class).values())
                            .hasAtLeastOneElementOfType(JimmerNameResolver.class);
                });
        runner.withBean(JSqlClient.class, () -> mock(JSqlClientImplementor.class))
                .withPropertyValues("intentflow.executor=jdbc")
                .run(ctx -> assertThat(ctx.getBean(QueryExecutor.class)).isInstanceOf(JdbcQueryExecutor.class));
    }

    @Test
    void springDataJdbcWinsOverPlainJdbcWhenItsConverterIsPresent() {
        runner.withBean(JdbcConverter.class, () -> new MappingJdbcConverter(new JdbcMappingContext(),
                (identifier, path) -> List.of()))
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx.getBean(QueryExecutor.class)).isInstanceOf(SpringDataJdbcQueryExecutor.class);
                    assertThat(ctx.getBeansOfType(NameResolver.class).values())
                            .hasAtLeastOneElementOfType(SpringDataJdbcNameResolver.class);
                });
    }

    @Test
    void cassandraOnlyApplicationStartsWithoutAnyJdbcClasses() {
        new ApplicationContextRunner()
                .withClassLoader(new FilteredClassLoader("org.springframework.boot.jdbc", "org.springframework.jdbc"))
                .withConfiguration(AutoConfigurations.of(IntentFlowAutoConfiguration.class))
                .withBean(CqlSession.class, () -> mock(CqlSession.class))
                .withBean(CassandraConverter.class, MappingCassandraConverter::new)
                .withPropertyValues("intentflow.llm.model=m", "intentflow.catalog.packages=" + FIXTURES)
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx.getBean(QueryExecutor.class)).isInstanceOf(CassandraQueryExecutor.class);
                    assertThat(ctx).doesNotHaveBean(SqlDialect.class);
                });
    }

    @Test
    void missingAdapterJarsFallBackToJdbc() {
        runner.withClassLoader(new FilteredClassLoader(OpenMapperQueryExecutor.class, JpaQueryExecutor.class))
                .withConfiguration(AutoConfigurations.of(BootAutoConfigurations.HIBERNATE_JPA))
                .withBean(Procedures.class, () -> mock(Procedures.class))
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx.getBean(QueryExecutor.class)).isInstanceOf(JdbcQueryExecutor.class);
                    assertThat(ctx).doesNotHaveBean(OpenMapperNameResolver.class)
                            .doesNotHaveBean(JpaNameResolver.class);
                });
    }

    @Test
    void nameResolversFeedTheCatalogScan() {
        runner.withPropertyValues(
                "intentflow.catalog.packages=io.github.serfom256.intentflow.starter.autoconfigure.jpa")
                .withConfiguration(AutoConfigurations.of(BootAutoConfigurations.HIBERNATE_JPA))
                .run(ctx -> {
                    ServiceDescriptor service = ctx.getBean(ServiceCatalog.class).get(ServiceNames.of("shelves"));
                    assertThat(service.table().value()).isEqualTo("library_shelf");
                    assertThat(service.fields()).extracting(f -> f.column().value())
                            .containsExactly("shelf_id", "label_text");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class ManualDescriptor {
        @Bean
        ServiceDescriptor ordersService() {
            return ServiceDescriptors.of(
                    ServiceNames.of("orders"), null, TableNames.of("orders"),
                    List.of(FieldMappings.of(LogicalFieldNames.of("id"), ColumnNames.of("id"), true)),
                    List.of(), MaxTakes.of(10), UriTemplates.of("/orders/{{id}}"), Object.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class StubExecutor {
        final QueryExecutor executor = new QueryExecutor() {
            @Override
            public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
                return List.of();
            }
        };

        @Bean
        QueryExecutor queryExecutor() {
            return executor;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @AutoConfigurationPackage(basePackages = "io.github.serfom256.intentflow.starter.autoconfigure.other")
    static class OtherPackageApp {
    }
}
