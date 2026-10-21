package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.config.OpenMapperConfig;
import io.github.serfom256.intentflow.core.ast.factories.BoundQueries;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import io.github.serfom256.intentflow.starter.autoconfigure.openmapper.Tick;
import io.github.serfom256.procedures.ProceduresCustomizer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenMapperStatementTimeoutTest {

    private static final String SLOW = "SELECT a.x AS x FROM SYSTEM_RANGE(1, 1000000) a, SYSTEM_RANGE(1, 1000000) b "
            + "WHERE a.x + b.x < 0";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(BootAutoConfigurations.DATA_SOURCE, OpenMapperConfig.class,
                    IntentFlowAutoConfiguration.class))
            .withBean(LlmClient.class, () -> (text, catalog) -> DslSources.of("books"))
            .withPropertyValues("spring.datasource.url=jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1",
                    "openmapper.model.packagesToScan=io.github.serfom256.intentflow.starter.autoconfigure.openmapper",
                    "intentflow.llm.model=m",
                    "intentflow.catalog.packages=io.github.serfom256.intentflow.starter.autoconfigure.fixtures",
                    "intentflow.schema.introspect=false", "intentflow.sql.statement-timeout=1s");

    @Test
    void theStatementTimeoutCancelsASlowEntityQuery() {
        runner.run(ctx -> {
            QueryExecutor executor = ctx.getBean(QueryExecutor.class);
            long start = System.nanoTime();

            assertThatThrownBy(() -> executor.execute(BoundQueries.of(SLOW), Tick.class))
                    .hasStackTraceContaining("cancel");
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(4));
        });
    }

    @Test
    void theApplicationsCustomizersStillApply() {
        DataSource reports = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        JdbcClient.create(reports).sql("CREATE TABLE tick (x BIGINT)").update();
        JdbcClient.create(reports).sql("INSERT INTO tick VALUES (7)").update();

        runner.withBean(ProceduresCustomizer.class, () -> builder -> builder.dataSource(reports))
                .run(ctx -> assertThat(ctx.getBean(QueryExecutor.class)
                        .execute(BoundQueries.of("SELECT x FROM tick"), Tick.class))
                        .extracting(tick -> tick.x).containsExactly(7L));
    }
}
