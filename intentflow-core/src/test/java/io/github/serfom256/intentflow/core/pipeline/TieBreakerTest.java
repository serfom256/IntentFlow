package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.pipeline.factories.AstValidators;
import io.github.serfom256.intentflow.core.pipeline.factories.DslParsers;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.core.schema.SchemaFacts;
import io.github.serfom256.intentflow.core.schema.TableFacts;
import io.github.serfom256.intentflow.core.testing.TestDialect;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TieBreakerTest {

    @IntentService(value = "orders", table = "orders", uri = "/orders/{{id}}", defaultSort = { "created_at desc",
            "id desc" })
    record Order(@IntentField long id, @IntentField BigDecimal total,
            @IntentField(name = "created_at") LocalDateTime createdAt) {
    }

    private static final ServiceCatalog CATALOG = ServiceCatalogs.fromAnnotated(Order.class);
    private static final SchemaFacts KEYED = SchemaFacts
            .of(Map.of("orders", new TableFacts(List.of("id"), List.of(), Set.of("total", "created_at"), Map.of())));

    private static final SqlBuilder WITH_KEY = SqlBuilders.create(new TestDialect(), KEYED);
    private static final SqlBuilder WITHOUT_FACTS = SqlBuilders.create(new TestDialect());

    private static ValidatedQuery query(String dsl) {
        return AstValidators.create().validate(DslParsers.create().parse(DslSources.of(dsl)), CATALOG);
    }

    private static List<String> order(SqlBuilder builder, String dsl) {
        return builder.order(query(dsl)).stream().map(k -> k.field().value() + " " + k.direction()).toList();
    }

    @Test
    void aRequestedSortIsBrokenByThePrimaryKeyAlone() {
        assertThat(WITH_KEY.build(query("orders | sort total desc")).sql())
                .endsWith("ORDER BY \"total\" DESC, \"id\" DESC LIMIT 50");
        assertThat(order(WITH_KEY, "orders | sort total desc")).containsExactly("total DESC", "id DESC");
    }

    @Test
    void theKeyKeepsItsDirectionFromTheDefaultSortOrIsAscending() {
        assertThat(order(WITH_KEY, "orders | sort id")).containsExactly("id ASC");
        assertThat(order(WITH_KEY, "orders | sort total, created_at desc")).containsExactly("total ASC",
                "created_at DESC", "id DESC");
    }

    @Test
    void withoutAKnownKeyTiesFollowTheDefaultSort() {
        assertThat(order(WITHOUT_FACTS, "orders | sort total desc")).containsExactly("total DESC", "created_at DESC",
                "id DESC");
    }

    @Test
    void theDefaultSortAndAggregatesAreUnchanged() {
        assertThat(order(WITH_KEY, "orders")).containsExactly("created_at DESC", "id DESC");
        assertThat(WITH_KEY.order(query("orders | group total | count")))
                .isEqualTo(query("orders | group total | count").effectiveSort());
    }

    @Test
    void keysetPagingContinuesOnTheSameKeys() {
        ValidatedQuery sorted = query("orders | sort total desc");

        assertThat(WITH_KEY.supportsKeyset(sorted)).isTrue();
        assertThat(WITH_KEY
                .buildAfter(sorted, ResultShape.ROWS, type -> List.of(), List.of(new BigDecimal("10.00"), 7L)).sql())
                .contains("WHERE (\"total\" < ? OR (\"total\" = ? AND \"id\" < ?))");
    }

    @Test
    void theOrderIsTheEffectiveSortForBuildersWithoutKeys() {
        SqlBuilder plain = query -> {
            throw new UnsupportedOperationException();
        };

        assertThat(plain.order(query("orders | sort total desc"))).extracting(SortKey::field).extracting(f -> f.value())
                .containsExactly("total", "created_at", "id");
    }
}
