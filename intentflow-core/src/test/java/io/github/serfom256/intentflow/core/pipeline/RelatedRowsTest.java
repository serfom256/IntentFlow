package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceDescriptors;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceNames;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.core.schema.SchemaFacts;
import io.github.serfom256.intentflow.dialect.postgresql.PostgresDialect;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

class RelatedRowsTest {

    @IntentService(value = "orders", table = "order", uri = "/orders/{{id}}", defaultSort = "id")
    record Order(@IntentField long id, @IntentField long customerId) {
    }

    private static final ServiceDescriptor ORDERS = ServiceDescriptors.fromAnnotated(Order.class);

    private static List<BoundQuery> related(List<Long> keys, SchemaFacts facts) {
        return SqlBuilders.create(new PostgresDialect(), facts).buildRelated(ORDERS, "customer_id", keys,
                type -> List.of());
    }

    private static SchemaFacts restricted() {
        return SchemaFacts.of(Map.of(), Map.of(ServiceNames.of("orders"), "deleted = FALSE"));
    }

    @Test
    void selectsTheRowsOfAllKeysWithTheTargetsRestriction() {
        List<BoundQuery> queries = related(List.of(1L, 2L, 3L), restricted());

        assertThat(queries).singleElement().satisfies(q -> {
            assertThat(q.sql()).isEqualTo(
                    "SELECT * FROM \"order\" WHERE \"customer_id\" IN (?, ?, ?, ?) AND (deleted = FALSE)");
            assertThat(q.values()).containsExactly(1L, 2L, 3L, 3L);
        });
    }

    @Test
    void padsKeyListsToFewStatementShapesAndSplitsLongOnes() {
        assertThat(related(List.of(7L), SchemaFacts.none()).getFirst().values()).containsExactly(7L);
        List<BoundQuery> queries = related(LongStream.rangeClosed(1, 1100).boxed().toList(), SchemaFacts.none());

        assertThat(queries).extracting(q -> q.values().size()).containsExactly(512, 512, 128);
        assertThat(queries).allSatisfy(q -> assertThat(q.sql()).doesNotContain("LIMIT"));
    }
}
