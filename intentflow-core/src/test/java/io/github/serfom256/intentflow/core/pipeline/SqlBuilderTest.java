package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.dialect.postgresql.PostgresDialect;
import io.github.serfom256.intentflow.core.ast.And;
import io.github.serfom256.intentflow.core.ast.AstQuery;
import io.github.serfom256.intentflow.core.ast.BoolLiteral;
import io.github.serfom256.intentflow.core.ast.Compare;
import io.github.serfom256.intentflow.core.ast.ComparisonOp;
import io.github.serfom256.intentflow.core.ast.DslLiteral;
import io.github.serfom256.intentflow.core.ast.FilterExpr;
import io.github.serfom256.intentflow.core.ast.In;
import io.github.serfom256.intentflow.core.ast.IsNotNull;
import io.github.serfom256.intentflow.core.ast.IsNull;
import io.github.serfom256.intentflow.core.ast.Not;
import io.github.serfom256.intentflow.core.ast.NumberLiteral;
import io.github.serfom256.intentflow.core.ast.Or;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.TextLiteral;
import io.github.serfom256.intentflow.core.ast.factories.AstQueries;
import io.github.serfom256.intentflow.core.ast.factories.Skips;
import io.github.serfom256.intentflow.core.ast.factories.Takes;
import io.github.serfom256.intentflow.core.catalog.ColumnName;
import io.github.serfom256.intentflow.core.catalog.FieldMapping;
import io.github.serfom256.intentflow.core.catalog.LogicalFieldName;
import io.github.serfom256.intentflow.core.catalog.MaxTake;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import io.github.serfom256.intentflow.core.catalog.TableName;
import io.github.serfom256.intentflow.core.catalog.UriTemplate;
import io.github.serfom256.intentflow.core.catalog.factories.ColumnNames;
import io.github.serfom256.intentflow.core.catalog.factories.FieldMappings;
import io.github.serfom256.intentflow.core.catalog.factories.LogicalFieldNames;
import io.github.serfom256.intentflow.core.catalog.factories.MaxTakes;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceDescriptors;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceNames;
import io.github.serfom256.intentflow.core.catalog.factories.SortKeys;
import io.github.serfom256.intentflow.core.catalog.factories.TableNames;
import io.github.serfom256.intentflow.core.catalog.factories.UriTemplates;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.core.pipeline.factories.ValidatedQueries;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlBuilderTest {

    private final SqlBuilder sqlBuilder = SqlBuilders.create(new PostgresDialect());

    private static final ServiceName BOOKS = ServiceNames.of("books");
    private static final LogicalFieldName ID = LogicalFieldNames.of("id");
    private static final LogicalFieldName TITLE = LogicalFieldNames.of("title");
    private static final LogicalFieldName AUTHOR = LogicalFieldNames.of("author");
    private static final LogicalFieldName YEAR = LogicalFieldNames.of("year");
    private static final LogicalFieldName DELETED_AT = LogicalFieldNames.of("deletedAt");
    private static final LogicalFieldName SECRET = LogicalFieldNames.of("secret");

    private static ServiceDescriptor booksService(int maxTake, List<SortKey> defaultSort) {
        return ServiceDescriptors.of(
                BOOKS, null, TableNames.of("book"),
                List.of(
                        FieldMappings.of(ID, ColumnNames.of("id"), true),
                        FieldMappings.of(TITLE, ColumnNames.of("title"), true),
                        FieldMappings.of(AUTHOR, ColumnNames.of("author_name"), true),
                        FieldMappings.of(YEAR, ColumnNames.of("year"), true),
                        FieldMappings.of(DELETED_AT, ColumnNames.of("deleted_at"), true),
                        FieldMappings.of(SECRET, ColumnNames.of("secret_col"), false)),
                defaultSort,
                MaxTakes.of(maxTake),
                UriTemplates.of("/books?ids={{ids}}"),
                Object.class);
    }

    private BoundQuery build(FilterExpr filter, List<SortKey> sort,
            io.github.serfom256.intentflow.core.ast.Skip skip, io.github.serfom256.intentflow.core.ast.Take take,
            ServiceDescriptor svc) {
        AstQuery ast = AstQueries.of(BOOKS, filter, sort, skip, take);
        return sqlBuilder.build(ValidatedQueries.of(svc, ast));
    }

    @Test
    void alwaysSelectsEveryColumnSoTheExecutorCanMapWholeEntities() {
        BoundQuery r = build(null, List.of(), null, null, booksService(100, List.of()));
        assertThat(r.sql()).startsWith("SELECT * FROM \"book\"");
    }

    @Test
    void identifiersAreDoubleQuotedAndEscaped() {
        ServiceDescriptor svc = ServiceDescriptors.of(
                BOOKS, null, TableNames.of("we\"ird"),
                List.of(FieldMappings.of(ID, ColumnNames.of("i\"d"), true)),
                List.of(), MaxTakes.of(10), UriTemplates.of("/x"), Object.class);
        BoundQuery r = build(null, List.of(SortKeys.of(ID, SortKey.Direction.ASC)), null, null, svc);
        assertThat(r.sql()).isEqualTo("SELECT * FROM \"we\"\"ird\" ORDER BY \"i\"\"d\" ASC LIMIT 10");
    }

    @Test
    void segmentsSplitAroundEachParameter() {
        FilterExpr f = new And(
                new Compare(YEAR, ComparisonOp.GE, new NumberLiteral(new BigDecimal("2000"))),
                new Compare(YEAR, ComparisonOp.LT, new NumberLiteral(new BigDecimal("2010"))));
        BoundQuery r = build(f, List.of(), null, null, booksService(100, List.of()));

        assertThat(r.segments()).hasSize(3);
        assertThat(r.render(i -> "?" + i)).contains("\"year\" >= ?1 AND \"year\" < ?2");
        assertThat(r.render(i -> "[p" + i + "]")).contains("\"year\" >= [p1] AND \"year\" < [p2]");
        assertThat(r.values()).containsExactly(new BigDecimal("2000"), new BigDecimal("2010"));
    }

    @Test
    void everyComparisonOperatorMapsToSql() {
        assertOp(ComparisonOp.EQ, "=");
        assertOp(ComparisonOp.NE, "<>");
        assertOp(ComparisonOp.LT, "<");
        assertOp(ComparisonOp.LE, "<=");
        assertOp(ComparisonOp.GT, ">");
        assertOp(ComparisonOp.GE, ">=");
    }

    private void assertOp(ComparisonOp op, String sqlOp) {
        FilterExpr f = new Compare(YEAR, op, new NumberLiteral(new BigDecimal("2000")));
        BoundQuery r = build(f, List.of(), null, null,
                booksService(100, List.of()));
        assertThat(r.sql()).contains("WHERE \"year\" " + sqlOp + " ?");
        assertThat(r.parameters()).containsExactly(new NumberLiteral(new BigDecimal("2000")));
    }

    @Test
    void containsBecomesIlikeWithWrappedAndEscapedParam() {
        FilterExpr f = new Compare(TITLE, ComparisonOp.CONTAINS, new TextLiteral("ring"));
        BoundQuery r = build(f, List.of(), null, null,
                booksService(100, List.of()));
        assertThat(r.sql()).contains("\"title\" ILIKE ? ESCAPE '\\'");
        assertThat(r.parameters()).containsExactly(new TextLiteral("%ring%"));
    }

    @Test
    void containsEscapesLikeMetaInUserValue() {
        FilterExpr f = new Compare(TITLE, ComparisonOp.CONTAINS, new TextLiteral("50%_off\\sale"));
        BoundQuery r = build(f, List.of(), null, null,
                booksService(100, List.of()));
        assertThat(r.parameters()).containsExactly(new TextLiteral("%50\\%\\_off\\\\sale%"));
    }

    @Test
    void containsWithNonTextValueIsRejected() {
        FilterExpr f = new Compare(YEAR, ComparisonOp.CONTAINS, new NumberLiteral(BigDecimal.ONE));
        assertThatThrownBy(() -> build(f, List.of(), null, null,
                booksService(100, List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CONTAINS");
    }

    @Test
    void andOrNotCompose() {
        FilterExpr f = new And(
                new Compare(AUTHOR, ComparisonOp.EQ, new TextLiteral("Doe")),
                new Or(
                        new Compare(YEAR, ComparisonOp.GE, new NumberLiteral(new BigDecimal("2000"))),
                        new Not(new Compare(YEAR, ComparisonOp.EQ, new NumberLiteral(BigDecimal.ONE)))));
        BoundQuery r = build(f, List.of(), null, null,
                booksService(100, List.of()));
        assertThat(r.sql()).contains(
                "WHERE (LOWER(\"author_name\") = LOWER(?) AND (\"year\" >= ? OR NOT (\"year\" = ?)))");
        assertThat(r.parameters()).hasSize(3);
    }

    @Test
    void textNotEqualIsCaseInsensitive() {
        BoundQuery r = build(new Compare(AUTHOR, ComparisonOp.NE, new TextLiteral("Doe")),
                List.of(), null, null, booksService(100, List.of()));
        assertThat(r.sql()).contains("WHERE LOWER(\"author_name\") <> LOWER(?)");
    }

    @Test
    void textOrderingComparisonIsLeftAsIs() {
        BoundQuery r = build(new Compare(TITLE, ComparisonOp.LT, new TextLiteral("M")),
                List.of(), null, null, booksService(100, List.of()));
        assertThat(r.sql()).contains("WHERE \"title\" < ?");
    }

    @Test
    void numericInListIsNotLowered() {
        FilterExpr f = new In(YEAR, List.of(
                new NumberLiteral(new BigDecimal("1999")), new NumberLiteral(new BigDecimal("2000"))));
        BoundQuery r = build(f, List.of(), null, null,
                booksService(100, List.of()));
        assertThat(r.sql()).contains("WHERE \"year\" IN (?, ?)");
    }

    @Test
    void inListGeneratesPlaceholdersForEachValue() {
        FilterExpr f = new In(TITLE,
                List.of(new TextLiteral("a"), new TextLiteral("b"), new TextLiteral("c")));
        BoundQuery r = build(f, List.of(), null, null,
                booksService(100, List.of()));
        assertThat(r.sql()).contains("LOWER(\"title\") IN (LOWER(?), LOWER(?), LOWER(?))");
        assertThat(r.parameters()).hasSize(3);
    }

    @Test
    void isNullAndIsNotNullEmitWithoutParams() {
        BoundQuery n1 = build(new IsNull(DELETED_AT), List.of(), null, null,
                booksService(100, List.of()));
        assertThat(n1.sql()).contains("\"deleted_at\" IS NULL");
        assertThat(n1.parameters()).isEmpty();

        BoundQuery n2 = build(new IsNotNull(DELETED_AT), List.of(), null, null,
                booksService(100, List.of()));
        assertThat(n2.sql()).contains("\"deleted_at\" IS NOT NULL");
        assertThat(n2.parameters()).isEmpty();
    }

    @Test
    void astSortBeatsCatalogDefaultSort() {
        List<SortKey> catalogDefault = List.of(SortKeys.of(ID, SortKey.Direction.ASC));
        BoundQuery r = build(null,
                List.of(SortKeys.of(YEAR, SortKey.Direction.DESC),
                        SortKeys.of(TITLE, SortKey.Direction.ASC)),
                null, null, booksService(100, catalogDefault));
        assertThat(r.sql()).contains("ORDER BY \"year\" DESC NULLS LAST, \"title\" ASC");
    }

    @Test
    void emptyAstSortFallsBackToCatalogDefault() {
        List<SortKey> catalogDefault = List.of(SortKeys.of(ID, SortKey.Direction.ASC));
        BoundQuery r = build(null, List.of(), null, null,
                booksService(100, catalogDefault));
        assertThat(r.sql()).contains("ORDER BY \"id\" ASC");
    }

    @Test
    void noSortAnywhereOmitsOrderBy() {
        BoundQuery r = build(null, List.of(), null, null,
                booksService(100, List.of()));
        assertThat(r.sql()).doesNotContain("ORDER BY");
    }

    @Test
    void astTakeBeatsCatalogMaxTake() {
        BoundQuery r = build(null, List.of(), null, Takes.of(20),
                booksService(100, List.of()));
        assertThat(r.sql()).contains("LIMIT 20");
    }

    @Test
    void missingTakeFallsBackToMaxTake() {
        BoundQuery r = build(null, List.of(), null, null,
                booksService(50, List.of()));
        assertThat(r.sql()).contains("LIMIT 50");
    }

    @Test
    void skipEmitsOffsetWhenPositive() {
        BoundQuery r = build(null, List.of(), Skips.of(40), Takes.of(20),
                booksService(100, List.of()));
        assertThat(r.sql()).contains("LIMIT 20").contains("OFFSET 40");
    }

    @Test
    void skipZeroOmitsOffset() {
        BoundQuery r = build(null, List.of(), Skips.of(0), Takes.of(20),
                booksService(100, List.of()));
        assertThat(r.sql()).doesNotContain("OFFSET");
    }

    @Test
    void missingSkipOmitsOffset() {
        BoundQuery r = build(null, List.of(), null, Takes.of(20),
                booksService(100, List.of()));
        assertThat(r.sql()).doesNotContain("OFFSET");
    }

    @Test
    void fullQueryAssemblesInCanonicalOrder() {
        FilterExpr f = new And(
                new Compare(AUTHOR, ComparisonOp.EQ, new TextLiteral("Doe")),
                new Compare(YEAR, ComparisonOp.GE, new NumberLiteral(new BigDecimal("2000"))));
        BoundQuery r = build(
                f,
                List.of(SortKeys.of(YEAR, SortKey.Direction.DESC)),
                Skips.of(20),
                Takes.of(10),
                booksService(100, List.of()));

        assertThat(r.sql()).isEqualTo(
                "SELECT * FROM \"book\""
                        + " WHERE (LOWER(\"author_name\") = LOWER(?) AND \"year\" >= ?)"
                        + " ORDER BY \"year\" DESC NULLS LAST"
                        + " LIMIT 10 OFFSET 20");
        assertThat(r.parameters()).containsExactly(
                new TextLiteral("Doe"),
                new NumberLiteral(new BigDecimal("2000")));
    }

    @Test
    void boolParameterFlowsThrough() {
        FilterExpr f = new Compare(LogicalFieldNames.of("year"),
                ComparisonOp.EQ, new BoolLiteral(true));
        BoundQuery r = build(f, List.of(), null, null,
                booksService(100, List.of()));
        assertThat(r.parameters()).containsExactly(new BoolLiteral(true));
    }

    @Test
    void parametersListIsImmutable() {
        BoundQuery r = build(new Compare(YEAR, ComparisonOp.EQ, new NumberLiteral(BigDecimal.ONE)),
                List.of(), null, null, booksService(100, List.of()));
        assertThatThrownBy(() -> r.parameters().add(new NumberLiteral(BigDecimal.TEN)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void columnContainingDoubleQuoteIsEscaped() {
        ServiceDescriptor svc = ServiceDescriptors.of(
                BOOKS, null, TableNames.of("book"),
                List.of(FieldMappings.of(
                        LogicalFieldNames.of("weird"),
                        ColumnNames.of("we\"ird"),
                        true)),
                List.of(),
                MaxTakes.of(10),
                UriTemplates.of("/x"),
                Object.class);
        BoundQuery r = sqlBuilder.build(ValidatedQueries.of(svc, AstQueries.of(BOOKS, null,
                List.of(SortKeys.of(LogicalFieldNames.of("weird"), SortKey.Direction.ASC)), null, null)));
        assertThat(r.sql()).contains("ORDER BY \"we\"\"ird\" ASC");
    }
}
