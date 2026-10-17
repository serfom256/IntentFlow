package io.github.serfom256.intentflow.dialect.mysql;

import io.github.serfom256.intentflow.core.ast.AstQuery;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.Compare;
import io.github.serfom256.intentflow.core.ast.ComparisonOp;
import io.github.serfom256.intentflow.core.ast.FilterExpr;
import io.github.serfom256.intentflow.core.ast.In;
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

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MySqlDialectTest {

    private static final ServiceName BOOKS = ServiceNames.of("books");
    private static final LogicalFieldName TITLE = LogicalFieldNames.of("title");
    private static final LogicalFieldName RATING = LogicalFieldNames.of("rating");
    private static final LogicalFieldName PUBLISHED = LogicalFieldNames.of("published_at");

    private static final ServiceDescriptor SERVICE = ServiceDescriptors.of(
            BOOKS, null, TableNames.of("book`s"),
            List.of(
                    new DefaultFieldMapping(TITLE, ColumnNames.of("title"), true, FieldType.TEXT, null, List.of(),
                            "title"),
                    new DefaultFieldMapping(RATING, ColumnNames.of("rating"), true, FieldType.NUMBER, null, List.of(),
                            "rating"),
                    new DefaultFieldMapping(PUBLISHED, ColumnNames.of("published_at"), true, FieldType.TIMESTAMP, null,
                            List.of(), "publishedAt")),
            List.of(), MaxTakes.of(50), UriTemplates.of("/books"), Object.class);

    private final SqlBuilder builder = SqlBuilders.create(new MySqlDialect(), SchemaFacts.none(),
            EnumSet.allOf(Optimization.class), ZoneOffset.UTC);

    private BoundQuery build(@Nullable FilterExpr filter, List<SortKey> sort) {
        AstQuery ast = AstQueries.of(BOOKS, filter, sort, null, null);
        return builder.build(ValidatedQueries.of(SERVICE, ast));
    }

    @Test
    void quotesIdentifiersWithBackticks() {
        assertThat(build(null, List.of()).sql()).isEqualTo("SELECT * FROM `book``s` LIMIT 50");
    }

    @Test
    void containsLowersBothSidesAndEscapesWithBang() {
        BoundQuery q = build(new Compare(TITLE, ComparisonOp.CONTAINS, new TextLiteral("50%_off!\\")), List.of());

        assertThat(q.sql()).contains("WHERE LOWER(`title`) LIKE LOWER(?) ESCAPE '!'");
        assertThat(q.parameters()).containsExactly(new TextLiteral("%50!%!_off!!\\%"));
    }

    @Test
    void timestampLiteralsAreBoundAsLocalDateTimes() {
        BoundQuery q = build(new In(PUBLISHED, List.of(
                new TextLiteral("2026-07-01T10:15"),
                new TextLiteral("2026-07-01 10:15:30.5"),
                new TextLiteral("2026-07-01T10:15:30+02:00"))), List.of());

        assertThat(q.sql()).contains("`published_at` IN (?, ?, ?)");
        assertThat(q.values()).containsExactly(
                LocalDateTime.parse("2026-07-01T10:15"),
                LocalDateTime.parse("2026-07-01T10:15:30.5"),
                LocalDateTime.parse("2026-07-01T08:15:30"));
    }

    @Test
    void aDateWithoutTimeCoversTheWholeDay() {
        BoundQuery q = build(new Compare(PUBLISHED, ComparisonOp.EQ, new TextLiteral("2026-07-01")), List.of());

        assertThat(q.sql()).contains("(`published_at` >= ? AND `published_at` < ?)");
        assertThat(q.values()).containsExactly(LocalDateTime.parse("2026-07-01T00:00"),
                LocalDateTime.parse("2026-07-02T00:00"));
    }

    @Test
    void sortPutsNullsLastInBothDirections() {
        BoundQuery q = build(null, List.of(
                SortKeys.of(RATING, SortKey.Direction.DESC), SortKeys.of(TITLE, SortKey.Direction.ASC)));

        assertThat(q.sql()).contains(
                "ORDER BY `rating` IS NULL, `rating` DESC, `title` IS NULL, `title` ASC LIMIT 50");
    }

    @Test
    void pagesWithLimitOffset() {
        AstQuery ast = AstQueries.of(BOOKS, null, List.of(), Skips.of(20), Takes.of(10));

        assertThat(builder.build(ValidatedQueries.of(SERVICE, ast)).sql()).endsWith(" LIMIT 10 OFFSET 20");
    }
}
