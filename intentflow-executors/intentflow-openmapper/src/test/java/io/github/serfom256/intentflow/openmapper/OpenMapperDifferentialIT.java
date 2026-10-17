package io.github.serfom256.intentflow.openmapper;

import io.github.serfom256.annotations.entity.Field;
import io.github.serfom256.annotations.entity.Model;
import io.github.serfom256.intentflow.core.catalog.NameResolver;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import io.github.serfom256.intentflow.core.testing.ExecutorDifferentialTest;
import io.github.serfom256.intentflow.core.testing.GadgetKind;
import io.github.serfom256.procedures.Procedures;
import org.junit.jupiter.api.AfterAll;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

class OpenMapperDifferentialIT extends ExecutorDifferentialTest {

    @Model(primaryKey = "id")
    @IntentService(value = "gadgets", table = "gadget", uri = "/gadgets/{{id}}", maxTake = 500, defaultSort = "id")
    static class Gadget {
        @Field
        @IntentField
        Long id;
        @Field
        @IntentField
        String name;
        @Field
        @IntentField(caseSensitive = true)
        GadgetKind kind;
        @Field
        @IntentField
        BigDecimal price;
        @Field
        @IntentField
        Integer stock;
        @Field
        @IntentField
        Boolean active;
        @Field(name = "created_at")
        @IntentField
        LocalDateTime createdAt;
    }

    @Override
    protected Class<?> entityType() {
        return Gadget.class;
    }

    @Override
    protected List<NameResolver> nameResolvers() {
        return List.of(new OpenMapperNameResolver());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class OpenMapperApp {
    }

    private ConfigurableApplicationContext context;

    @Override
    protected QueryExecutor createExecutor(String jdbcUrl, String user, String password) {
        context = new SpringApplicationBuilder(OpenMapperApp.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.datasource.url=" + jdbcUrl,
                        "spring.datasource.username=" + user,
                        "spring.datasource.password=" + password,
                        "openmapper.model.packagesToScan=io.github.serfom256.intentflow.openmapper")
                .run();
        return new OpenMapperQueryExecutor(context.getBean(Procedures.class),
                JdbcClient.create(context.getBean(DataSource.class)));
    }

    @AfterAll
    void close() {
        if (context != null) {
            context.close();
        }
    }
}
