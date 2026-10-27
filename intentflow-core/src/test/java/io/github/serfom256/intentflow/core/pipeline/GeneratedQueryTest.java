package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ast.AstQuery;
import io.github.serfom256.intentflow.core.ast.BoolLiteral;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.Compare;
import io.github.serfom256.intentflow.core.ast.ComparisonOp;
import io.github.serfom256.intentflow.core.ast.DslLiteral;
import io.github.serfom256.intentflow.core.ast.NumberLiteral;
import io.github.serfom256.intentflow.core.ast.TextLiteral;
import io.github.serfom256.intentflow.core.ast.factories.AstQueries;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.FieldMapping;
import io.github.serfom256.intentflow.core.catalog.FieldType;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import io.github.serfom256.intentflow.core.catalog.factories.ColumnNames;
import io.github.serfom256.intentflow.core.catalog.factories.FieldMappings;
import io.github.serfom256.intentflow.core.catalog.factories.LogicalFieldNames;
import io.github.serfom256.intentflow.core.catalog.factories.MaxTakes;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceDescriptors;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceNames;
import io.github.serfom256.intentflow.core.catalog.factories.SortKeys;
import io.github.serfom256.intentflow.core.catalog.factories.TableNames;
import io.github.serfom256.intentflow.core.catalog.factories.UriTemplates;
import io.github.serfom256.intentflow.core.pipeline.factories.AstValidators;
import io.github.serfom256.intentflow.core.pipeline.factories.DslParsers;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.core.testing.RandomQueries;
import io.github.serfom256.intentflow.core.testing.TestDialect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

class GeneratedQueryTest {

    private static final int QUERIES_PER_SEED = 300;

    static final ServiceDescriptor ITEMS = ServiceDescriptors.of(ServiceNames.of("items"), null,
            TableNames.of("item_table"),
            List.of(FieldMappings.of(LogicalFieldNames.of("id"), ColumnNames.of("item_id"), true, FieldType.NUMBER),
                    FieldMappings.of(LogicalFieldNames.of("name"), ColumnNames.of("item_name"), true, FieldType.TEXT),
                    FieldMappings.of(LogicalFieldNames.of("category"), ColumnNames.of("cat"), true, FieldType.TEXT,
                            null, List.of("LAPTOP", "PHONE", "SMART_WATCH")),
                    FieldMappings.of(LogicalFieldNames.of("price"), ColumnNames.of("price_usd"), true,
                            FieldType.NUMBER),
                    FieldMappings.of(LogicalFieldNames.of("active"), ColumnNames.of("is_active"), true,
                            FieldType.BOOLEAN),
                    FieldMappings.of(LogicalFieldNames.of("created"), ColumnNames.of("created_at"), true,
                            FieldType.TIMESTAMP),
                    FieldMappings.of(LogicalFieldNames.of("notes"), ColumnNames.of("notes"), true),
                    FieldMappings.of(LogicalFieldNames.of("secret"), ColumnNames.of("secret"), false, FieldType.TEXT)),
            List.of(SortKeys.of(LogicalFieldNames.of("id"), SortKey.Direction.ASC)), MaxTakes.of(40),
            UriTemplates.of("/items/{{id}}?s={{secret}}"), Object.class);

    private static final ServiceCatalog CATALOG = ServiceCatalogs.of(ITEMS);
    private static final Set<String> IDENTIFIERS = Set.of("item_table", "item_id", "item_name", "cat", "price_usd",
            "is_active", "created_at", "notes", "secret", "intentflow_rank");

    private static final Pattern SQL_TOKEN = Pattern
            .compile("\\s+|\"(?:[^\"]|\"\")+\"|`(?:[^`]|``)+`|\\?|\\.|\\*|\\+|-|/|,|\\(|\\)|<>|<=|>=|=|<|>|'\\\\'|'!'"
                    + "|'(?:year|month|day|hour)'|'%[A-Za-z0-9%: -]+'|\\d+"
                    + "|(?:SELECT|FROM|WHERE|AND|OR|NOT|IN|IS|NULL|LOWER|UPPER|CAST|AS|TIMESTAMP|DATETIME|ILIKE|LIKE"
                    + "|ESCAPE|ORDER|BY|ASC|DESC|NULLS|LAST|LIMIT|OFFSET|COALESCE|EXTRACT|YEAR|MONTH|DAY|HOUR"
                    + "|DATE_TRUNC|DATE_FORMAT|DATE|CHAR_LENGTH|ROUND|ABS|NUMERIC|BIGINT|TEXT|DECIMAL|SIGNED|CHAR"
                    + "|NULLIF|ROW_NUMBER|OVER|PARTITION|BINARY)\\b");

    private final DslParser parser = DslParsers.create();
    private final AstValidator validator = AstValidators.create();
    private final SqlBuilder sqlBuilder = SqlBuilders.create(new TestDialect());

    static LongStream seeds() {
        return LongStream.rangeClosed(1, 20);
    }

    @ParameterizedTest(name = "seed {0}")
    @MethodSource("seeds")
    void printedQueriesParseBackToTheSameAst(long seed) {
        RandomQueries gen = new RandomQueries(seed, ITEMS);
        for (int i = 0; i < QUERIES_PER_SEED; i++) {
            AstQuery q = gen.query();
            String dsl = RandomQueries.print(q);
            AstQuery parsed;
            try {
                parsed = parser.parse(DslSources.of(dsl));
            } catch (RuntimeException e) {
                throw new AssertionError("seed " + seed + " #" + i + " failed to parse: " + dsl, e);
            }
            assertThat(parsed).as("seed %d #%d: %s", seed, i, dsl).isEqualTo(q);
        }
    }

    @ParameterizedTest(name = "seed {0}")
    @MethodSource("seeds")
    void typeCorrectQueriesValidateAndCompileToSafeSql(long seed) {
        RandomQueries gen = new RandomQueries(seed, ITEMS);
        for (int i = 0; i < QUERIES_PER_SEED; i++) {
            AstQuery q = gen.query();
            String dsl = RandomQueries.print(q);
            BoundQuery sql = sqlBuilder.build(validator.validate(q, CATALOG));
            String text = sql.sql();

            assertThat(count(text, '?')).as("placeholders vs binds: %s", dsl).isEqualTo(sql.parameters().size());
            assertOnlyKnownTokens(text, dsl);
            assertThat(text).as(dsl).startsWith(
                    q.top().isEmpty() ? "SELECT * FROM \"item_table\"" : "SELECT * FROM (SELECT \"item_table\".*")
                    .contains(" LIMIT ");
            for (DslLiteral p : sql.parameters()) {
                assertThat(p).as(dsl).isNotNull();
            }

        }
    }

    @ParameterizedTest(name = "seed {0}")
    @MethodSource("seeds")
    void wrongTypedLiteralsAreRejected(long seed) {
        Random rnd = new Random(seed);
        List<FieldMapping> typed = ITEMS.fields().stream().filter(f -> f.queryable() && f.type() != FieldType.ANY)
                .toList();
        for (int i = 0; i < 100; i++) {
            FieldMapping f = typed.get(rnd.nextInt(typed.size()));
            DslLiteral wrong = wrongLiteral(f, rnd);
            AstQuery q = AstQueries.of(ITEMS.name(), new Compare(f.logical(), ComparisonOp.EQ, wrong), List.of(), null,
                    null);
            assertThatThrownBy(() -> validator.validate(q, CATALOG))
                    .as("%s == %s", f.logical().value(), RandomQueries.print(wrong))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("field '" + f.logical().value() + "'");
        }
    }

    @ParameterizedTest(name = "seed {0}")
    @MethodSource("seeds")
    void corruptedDslOnlyFailsWithParseErrors(long seed) {
        RandomQueries gen = new RandomQueries(seed, ITEMS);
        Random rnd = new Random(seed * 31);
        String junk = "|()\",.~=!<>-\\ \n\tunsupportedfilter0123456789";
        for (int i = 0; i < QUERIES_PER_SEED; i++) {
            StringBuilder dsl = new StringBuilder(RandomQueries.print(gen.query()));
            int edits = 1 + rnd.nextInt(4);
            for (int e = 0; e < edits && !dsl.isEmpty(); e++) {
                int at = rnd.nextInt(dsl.length());
                switch (rnd.nextInt(3)) {
                case 0 -> dsl.deleteCharAt(at);
                case 1 -> dsl.insert(at, dsl.charAt(rnd.nextInt(dsl.length())));
                default -> dsl.insert(at, junk.charAt(rnd.nextInt(junk.length())));
                }
            }
            try {
                parser.parse(DslSources.of(dsl.toString()));
            } catch (DslParseException | UnsupportedRequestException expected) {
            } catch (RuntimeException unexpected) {
                fail("seed " + seed + " #" + i + " threw " + unexpected + " for: " + dsl, unexpected);
            }
        }
    }

    @Test
    void containsBindsAreWrappedAndEscaped() {
        AstQuery q = AstQueries.of(ITEMS.name(),
                new Compare(LogicalFieldNames.of("name"), ComparisonOp.CONTAINS, new TextLiteral("50%_off\\")),
                List.of(), null, null);

        BoundQuery sql = sqlBuilder.build(validator.validate(q, CATALOG));

        assertThat(sql.sql()).contains("\"item_name\" ILIKE ? ESCAPE '\\'");
        assertThat(sql.parameters()).containsExactly(new TextLiteral("%50\\%\\_off\\\\%"));
    }

    private static DslLiteral wrongLiteral(FieldMapping f, Random rnd) {
        return switch (f.type()) {
        case TEXT -> f.allowedValues().isEmpty() || rnd.nextBoolean() ? new NumberLiteral(BigDecimal.TEN)
                : new TextLiteral("NOT_A_" + f.allowedValues().getFirst());
        case NUMBER -> rnd.nextBoolean() ? new TextLiteral("10") : new BoolLiteral(true);
        case BOOLEAN -> rnd.nextBoolean() ? new TextLiteral("true") : new NumberLiteral(BigDecimal.ONE);
        case TIMESTAMP -> rnd.nextBoolean() ? new TextLiteral("yesterday") : new NumberLiteral(BigDecimal.ONE);
        case ANY -> throw new IllegalArgumentException("untyped");
        };
    }

    private static void assertOnlyKnownTokens(String sql, String dsl) {
        Matcher m = SQL_TOKEN.matcher(sql);
        int pos = 0;
        while (pos < sql.length()) {
            m.region(pos, sql.length());
            if (!m.lookingAt()) {
                fail("unexpected SQL text at " + pos + ": '" + sql.substring(pos) + "'\nSQL: " + sql + "\nDSL: " + dsl);
            }
            String token = m.group();
            if (token.startsWith("\"") || token.startsWith("`")) {
                String quote = token.substring(0, 1);
                assertThat(IDENTIFIERS).as("identifier in %s", sql)
                        .contains(token.substring(1, token.length() - 1).replace(quote + quote, quote));
            }
            pos = m.end();
        }
    }

    private static int count(String s, char c) {
        return (int) s.chars().filter(ch -> ch == c).count();
    }
}
