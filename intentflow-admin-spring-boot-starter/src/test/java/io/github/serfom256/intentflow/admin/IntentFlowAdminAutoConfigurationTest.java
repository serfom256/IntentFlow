package io.github.serfom256.intentflow.admin;

import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.CatalogOverrides;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.impl.InMemoryCatalogOverrides;
import io.github.serfom256.intentflow.core.catalog.impl.OverlayServiceCatalog;
import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import io.github.serfom256.intentflow.starter.autoconfigure.IntentFlowAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IntentFlowAdminAutoConfigurationTest {

    @EnableWebMvc
    static class Mvc {
    }

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(
                    AutoConfigurations.of(IntentFlowAutoConfiguration.class, IntentFlowAdminAutoConfiguration.class))
            .withUserConfiguration(Mvc.class)
            .withPropertyValues("intentflow.catalog.packages=io.github.serfom256.intentflow.admin",
                    "intentflow.admin.security.enabled=false")
            .withBean(LlmClient.class, () -> (text, catalog) -> DslSources.of("books | take 1"))
            .withBean(QueryExecutor.class, () -> new QueryExecutor() {
                @Override
                public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
                    return List.of();
                }
            });

    @Test
    void canBeSwitchedOff() {
        runner.withPropertyValues("intentflow.admin.enabled=false").run(ctx -> {
            assertThat(ctx).doesNotHaveBean(CatalogOverrides.class).doesNotHaveBean(CatalogAdminController.class);
            assertThat(ctx.getBean(ServiceCatalog.class)).isNotInstanceOf(OverlayServiceCatalog.class);
        });
    }

    @Test
    void isOnAsSoonAsTheStarterIsPresentAndKeepsOverridesInMemoryWithoutADataSource() {
        runner.run(ctx -> {
            assertThat(ctx.getBean(CatalogOverrides.class)).isInstanceOf(InMemoryCatalogOverrides.class);
            assertThat(ctx.getBean(ServiceCatalog.class)).isInstanceOf(OverlayServiceCatalog.class);
        });
    }

    @Test
    void keepsOverridesInATableWhenADataSourceExists() {
        runner.withBean(DataSource.class,
                () -> new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""))
                .run(ctx -> assertThat(ctx.getBean(CatalogOverrides.class)).isInstanceOf(JdbcCatalogOverrides.class));
        runner.withPropertyValues("intentflow.admin.storage=memory").withBean(DataSource.class,
                () -> new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""))
                .run(ctx -> assertThat(ctx.getBean(CatalogOverrides.class))
                        .isInstanceOf(InMemoryCatalogOverrides.class));
    }

    @Test
    void jdbcStorageWithoutADataSourceFailsStartup() {
        runner.withPropertyValues("intentflow.admin.storage=jdbc").run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void servesTheApiAndTheUiUnderAConfiguredPath() {
        runner.withPropertyValues("intentflow.admin.path=/ops/catalog").run(ctx -> {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(ctx).build();
            mvc.perform(get("/ops/catalog/api/services")).andExpect(status().isOk());
            mvc.perform(get("/ops/catalog")).andExpect(redirectedUrl("/ops/catalog/"));
            mvc.perform(get("/ops/catalog/")).andExpect(forwardedUrl("/ops/catalog/index.html"));
            mvc.perform(get("/ops/catalog/index.html")).andExpect(status().isOk())
                    .andExpect(content().string(containsString("IntentFlow Admin")));
            mvc.perform(get("/intentflow-admin/api/services")).andExpect(status().isNotFound());
        });
    }

    @Test
    void rejectsAMalformedPath() {
        runner.withPropertyValues("intentflow.admin.path=admin/").run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void servesTheApiAndTheUi() {
        runner.run(ctx -> {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(ctx).build();
            mvc.perform(get("/intentflow-admin/api/services")).andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].name").value("books"));
            mvc.perform(get("/intentflow-admin/")).andExpect(forwardedUrl("/intentflow-admin/index.html"));
            mvc.perform(get("/intentflow-admin/index.html")).andExpect(status().isOk())
                    .andExpect(content().string(containsString("IntentFlow Admin")));
        });
    }
}
