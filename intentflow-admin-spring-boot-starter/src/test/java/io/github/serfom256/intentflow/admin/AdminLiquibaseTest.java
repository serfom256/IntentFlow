package io.github.serfom256.intentflow.admin;

import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.CatalogOverrides;
import io.github.serfom256.intentflow.core.catalog.ServiceOverride;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceNames;
import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import io.github.serfom256.intentflow.starter.autoconfigure.IntentFlowAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdminLiquibaseTest {

    private static final Class<?> LIQUIBASE = resolve(
            "org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration",
            "org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration");

    @EnableWebMvc
    static class Mvc {
    }

    private final DataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LIQUIBASE, IntentFlowAutoConfiguration.class,
                    IntentFlowAdminAutoConfiguration.class))
            .withUserConfiguration(Mvc.class)
            .withBean(DataSource.class, () -> dataSource)
            .withBean(LlmClient.class, () -> (text, catalog) -> DslSources.of("books | take 1"))
            .withBean(QueryExecutor.class, () -> new QueryExecutor() {
                @Override
                public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
                    return List.of();
                }
            })
            .withPropertyValues("intentflow.catalog.packages=io.github.serfom256.intentflow.admin",
                    "intentflow.admin.security.enabled=false", "intentflow.admin.create-table=false",
                    "intentflow.schema.introspect=false", "intentflow.dialect=h2",
                    "spring.liquibase.change-log=classpath:" + JdbcCatalogOverrides.CHANGELOG);

    @Test
    void theChangelogCreatesTheTableBeforeTheAdminReadsIt() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            CatalogOverrides overrides = ctx.getBean(CatalogOverrides.class);
            overrides.put(ServiceNames.of("books"), new ServiceOverride("Every book", null, Map.of()));

            assertThat(new JdbcTemplate(dataSource).queryForObject(
                    "SELECT text_value FROM intentflow_catalog_override", String.class)).isEqualTo("Every book");
        });
    }

    @Test
    void theTableNameIsAChangelogParameter() {
        runner.withPropertyValues("intentflow.admin.table=catalog_edits",
                "spring.liquibase.parameters.intentflow.admin.table=catalog_edits").run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(new JdbcTemplate(dataSource).queryForObject(
                            "SELECT COUNT(*) FROM catalog_edits", Integer.class)).isZero();
                });
    }

    @Test
    void aTableTheAdminCreatedEarlierIsKept() {
        new JdbcCatalogOverrides(dataSource, "intentflow_catalog_override", true, Duration.ZERO)
                .put(ServiceNames.of("books"), new ServiceOverride("Kept", null, Map.of()));

        runner.run(ctx -> assertThat(ctx.getBean(CatalogOverrides.class).find(ServiceNames.of("books")))
                .map(ServiceOverride::description).contains("Kept"));
    }

    private static Class<?> resolve(String boot4, String boot3) {
        try {
            return Class.forName(boot4);
        } catch (ClassNotFoundException e) {
            try {
                return Class.forName(boot3);
            } catch (ClassNotFoundException missing) {
                throw new IllegalStateException("No LiquibaseAutoConfiguration on the classpath", missing);
            }
        }
    }
}
