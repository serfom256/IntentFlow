package io.github.serfom256.intentflow.dialect.h2;

import io.github.serfom256.intentflow.core.ast.AstQuery;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.Compare;
import io.github.serfom256.intentflow.core.ast.ComparisonOp;
import io.github.serfom256.intentflow.core.ast.FilterExpr;
import io.github.serfom256.intentflow.core.ast.NumberLiteral;
import io.github.serfom256.intentflow.core.ast.TextLiteral;
import io.github.serfom256.intentflow.core.ast.factories.AstQueries;
import io.github.serfom256.intentflow.core.ast.factories.Skips;
import io.github.serfom256.intentflow.core.ast.factories.Takes;
import io.github.serfom256.intentflow.core.catalog.FieldType;
import io.github.serfom256.intentflow.core.catalog.LogicalFieldName;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import io.github.serfom256.intentflow.core.catalog.factories.ColumnNames;
import io.github.serfom256.intentflow.core.catalog.factories.LogicalFieldNames;
import io.github.serfom256.intentflow.core.catalog.factories.MaxTakes;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceDescriptors;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceNames;
import io.github.serfom256.intentflow.core.catalog.factories.SortKeys;
import io.github.serfom256.intentflow.core.catalog.factories.TableNames;
import io.github.serfom256.intentflow.core.catalog.factories.UriTemplates;
import io.github.serfom256.intentflow.core.catalog.impl.DefaultFieldMapping;
import io.github.serfom256.intentflow.core.pipeline.SqlBuilder;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.core.pipeline.factories.ValidatedQueries;
import io.github.serfom256.intentflow.core.schema.SchemaFacts;
import io.github.serfom256.intentflow.core.sql.Optimization;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class H2DialectTest {

    private static final ServiceName BOOKS = ServiceNames.of("books");
    private static final LogicalFieldName TITLE = LogicalFieldNames.of("title");
    private static final LogicalFieldName RATING = LogicalFieldNames.of("rating");
    private static final LogicalFieldName PUBLISHED = LogicalFieldNames.of("published_at");

    private static final ServiceDescriptor SERVICE = ServiceDescriptors.of(BOOKS, null, TableNames.of("books"), List.of(
            new DefaultFieldMapping(TITLE, ColumnNames.of("title"), true, FieldType.TEXT, null, List.of(), "title"),
            new DefaultFieldMapping(RATING, ColumnNames.of("rating"), true, FieldType.NUMBER, null, List.of(),
                    "rating"),
            new DefaultFieldMapping(PUBLISHED, ColumnNames.of("published_at"), true, FieldType.TIMESTAMP, null,
                    List.of(), "publishedAt")),
            List.of(), MaxTakes.of(50), UriTemplates.of("/books"), Object.class);

    private final SqlBuilder builder = SqlBuilders.create(new H2Dialect(), SchemaFacts.none(),
            EnumSet.allOf(Optimization.class), ZoneOffset.UTC);

    private BoundQuery build(@Nullable FilterExpr filter, List<SortKey> sort) {
        AstQuery ast = AstQueries.of(BOOKS, filter, sort, null, null);
        return builder.build(ValidatedQueries.of(SERVICE, ast));
    }

    @Test
    void leavesPlainNamesUnquotedSoTheyMatchInEitherIdentifierCase() {
        H2Dialect dialect = new H2Dialect();

        assertThat(build(null, List.of()).sql()).isEqualTo("SELECT * FROM books FETCH FIRST 50 ROWS ONLY");
        assertThat(dialect.quote("created_at")).isEqualTo("created_at");
        assertThat(dialect.quote("CREATED_AT")).isEqualTo("CREATED_AT");
    }

    @Test
    void quotesKeywordsAndMixedCaseNames() {
        H2Dialect dialect = new H2Dialect();

        assertThat(dialect.quote("value")).isEqualTo("\"value\"");
        assertThat(dialect.quote("createdAt")).isEqualTo("\"createdAt\"");
        assertThat(dialect.quote("odd\"name")).isEqualTo("\"odd\"\"name\"");
    }

    @Test
    void containsUsesIlikeAndEscapesWithBackslash() {
        BoundQuery q = build(new Compare(TITLE, ComparisonOp.CONTAINS, new TextLiteral("50%_off\\")), List.of());

        assertThat(q.sql()).contains("WHERE title ILIKE ? ESCAPE '\\'");
        assertThat(q.parameters()).containsExactly(new TextLiteral("%50\\%\\_off\\\\%"));
    }

    @Test
    void fractionalNumbersKeepTheirScaleNextToIntegerColumns() {
        BoundQuery q = build(new Compare(RATING, ComparisonOp.GT, new NumberLiteral(new BigDecimal("-68.10"))),
                List.of());

        assertThat(q.sql()).contains("rating > CAST(? AS NUMERIC(3, 1))");
        assertThat(build(new Compare(RATING, ComparisonOp.GT, new NumberLiteral(new BigDecimal("0.005"))), List.of())
                .sql()).contains("rating > CAST(? AS NUMERIC(3, 3))");
        assertThat(
                build(new Compare(RATING, ComparisonOp.GT, new NumberLiteral(new BigDecimal("40.0"))), List.of()).sql())
                .contains("rating > ?");
    }

    @Test
    void sortPutsNullsLastInBothDirections() {
        BoundQuery q = build(null,
                List.of(SortKeys.of(RATING, SortKey.Direction.DESC), SortKeys.of(TITLE, SortKey.Direction.ASC)));

        assertThat(q.sql()).contains("ORDER BY rating DESC NULLS LAST, title ASC NULLS LAST FETCH FIRST 50 ROWS ONLY");
    }

    @Test
    void pagesWithOffsetFetch() {
        AstQuery ast = AstQueries.of(BOOKS, null, List.of(), Skips.of(20), Takes.of(10));

        assertThat(builder.build(ValidatedQueries.of(SERVICE, ast)).sql())
                .endsWith(" OFFSET 20 ROWS FETCH FIRST 10 ROWS ONLY");
    }
}
