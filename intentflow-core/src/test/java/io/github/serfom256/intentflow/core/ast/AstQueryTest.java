package io.github.serfom256.intentflow.core.ast;

import io.github.serfom256.intentflow.core.ast.factories.AstQueries;
import io.github.serfom256.intentflow.core.ast.factories.Skips;
import io.github.serfom256.intentflow.core.ast.factories.Takes;
import io.github.serfom256.intentflow.core.catalog.LogicalFieldName;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import io.github.serfom256.intentflow.core.catalog.factories.LogicalFieldNames;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceNames;
import io.github.serfom256.intentflow.core.catalog.factories.SortKeys;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AstQueryTest {

    private static final ServiceName BOOKS = ServiceNames.of("books");
    private static final LogicalFieldName AUTHOR = LogicalFieldNames.of("author");
    private static final LogicalFieldName YEAR = LogicalFieldNames.of("year");

    @Test
    void minimalServiceOnlyQuery() {
        AstQuery q = AstQueries.of(BOOKS, null, List.of(), null, null);
        assertThat(q.service()).isEqualTo(BOOKS);
        assertThat(q.filter()).isEmpty();
        assertThat(q.sort()).isEmpty();
        assertThat(q.skip()).isEmpty();
        assertThat(q.take()).isEmpty();
    }

    @Test
    void fullyPopulatedQuery() {
        FilterExpr filter = new And(new Compare(AUTHOR, ComparisonOp.EQ, new TextLiteral("Doe")),
                new Compare(YEAR, ComparisonOp.GE, new NumberLiteral(new BigDecimal("2000"))));

        AstQuery q = AstQueries.of(BOOKS, filter, List.of(SortKeys.of(YEAR, SortKey.Direction.DESC)), Skips.of(20),
                Takes.of(10));

        assertThat(q.filter()).containsSame(filter);
        assertThat(q.sort()).hasSize(1);
        assertThat(q.skip().orElseThrow().value()).isEqualTo(20);
        assertThat(q.take().orElseThrow().value()).isEqualTo(10);
    }

    @Test
    void sortIsDefensivelyCopiedAndImmutable() {
        List<SortKey> mutable = new ArrayList<>();
        mutable.add(SortKeys.of(YEAR, SortKey.Direction.ASC));
        AstQuery q = AstQueries.of(BOOKS, null, mutable, null, null);
        mutable.clear();
        assertThat(q.sort()).hasSize(1);
    }
}
