package io.github.serfom256.intentflow.dialect.mysql;

import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.pipeline.factories.AstValidators;
import io.github.serfom256.intentflow.core.pipeline.factories.DslParsers;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.core.schema.SchemaFacts;
import io.github.serfom256.intentflow.core.schema.TableFacts;
import io.github.serfom256.intentflow.core.sql.Optimization;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MySqlExpressionTest {

    @IntentService(value = "orders", table = "order", uri = "/orders/{{id}}", defaultSort = "id")
    record Order(@IntentField long id, @IntentField String code, @IntentField BigDecimal total,
            @IntentField Integer discount, @IntentField LocalDateTime createdAt,
            @IntentField LocalDateTime happenedAt) {
    }

    private static final ServiceCatalog CATALOG = ServiceCatalogs.fromAnnotated(Order.class);
    private static final SchemaFacts FACTS = SchemaFacts.of(Map.of("order",
            new TableFacts(List.of("id"), List.of(), Set.of(), Map.of(), Set.of("happened_at"))), Map.of());

    private static String sql(String dsl) {
        return SqlBuilders.create(new MySqlDialect(), FACTS, EnumSet.noneOf(Optimization.class),
                ZoneId.of("Europe/Berlin"))
                .build(AstValidators.create().validate(DslParsers.create().parse(DslSources.of(dsl)), CATALOG))
                .sql();
    }

    @Test
    void castsAndTruncationUseMySqlFunctions() {
        assertThat(sql("orders | filter code::bigint > 1000 and total / 3 > 1"))
                .contains("CAST(`code` AS SIGNED) > ?")
                .contains("(CAST(`total` AS DECIMAL(38, 10)) / NULLIF(?, 0)) > ?");
        assertThat(sql("orders | filter date_trunc(\"month\", created_at) >= \"2026-01-01T00:00:00\""))
                .contains("CAST(DATE_FORMAT(`created_at`, '%Y-%m-01') AS DATETIME(6)) >= CAST(? AS DATETIME(6))");
    }

    @Test
    void nullsSortLastThroughAnIsNullKey() {
        assertThat(sql("orders | sort coalesce(discount, 0) desc"))
                .endsWith("ORDER BY COALESCE(CAST(`discount` AS DECIMAL(38, 10)), ?) IS NULL, "
                        + "COALESCE(CAST(`discount` AS DECIMAL(38, 10)), ?) DESC, `id` IS NULL, `id` ASC LIMIT 50");
    }

    @Test
    void calendarPartsOfAZonedColumnUseTheSessionZone() {
        assertThat(sql("orders | filter hour(happened_at) == 9")).contains("EXTRACT(HOUR FROM `happened_at`)");
    }
}
