package io.github.serfom256.intentflow.core.testing;

import io.github.serfom256.intentflow.core.api.Aggregation;
import io.github.serfom256.intentflow.core.api.IntentResponse;
import io.github.serfom256.intentflow.core.api.factories.IntentRequests;
import io.github.serfom256.intentflow.core.ast.Aggregate;
import io.github.serfom256.intentflow.core.ast.AggregateFunction;
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
import io.github.serfom256.intentflow.core.ast.TextLiteral;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.FieldMapping;
import io.github.serfom256.intentflow.core.catalog.FieldType;
import io.github.serfom256.intentflow.core.catalog.LogicalFieldName;
import io.github.serfom256.intentflow.core.catalog.NameResolver;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceDescriptors;
import io.github.serfom256.intentflow.core.pipeline.IntentPipeline;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import io.github.serfom256.intentflow.core.pipeline.SqlBuilder;
import io.github.serfom256.intentflow.core.pipeline.ValidatedQuery;
import io.github.serfom256.intentflow.core.pipeline.factories.AstValidators;
import io.github.serfom256.intentflow.core.pipeline.factories.DslCaches;
import io.github.serfom256.intentflow.core.pipeline.factories.DslParsers;
import io.github.serfom256.intentflow.core.pipeline.factories.IntentPipelines;
import io.github.serfom256.intentflow.core.pipeline.factories.TextNormalizers;
import io.github.serfom256.intentflow.core.pipeline.factories.UriBuilders;
import io.github.serfom256.intentflow.core.pipeline.factories.ValidatedQueries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class DifferentialTestBase {

    private static final int ROWS = 200;
    private static final int QUERIES_PER_SEED = 150;
    private static final int AGGREGATES_PER_SEED = 40;

    private final List<Map<String, Object>> data = new ArrayList<>();
    private ServiceDescriptor gadgets;
    private Map<String, FieldMapping> fields;
    private QueryExecutor executor;

    protected abstract Class<?> entityType();

    protected abstract QueryExecutor prepare(List<Map<String, Object>> rows) throws Exception;

    protected abstract SqlBuilder sqlBuilder();

    protected abstract String target();

    protected List<NameResolver> nameResolvers() {
        return List.of();
    }

    static LongStream seeds() {
        return LongStream.rangeClosed(1, 20);
    }

    @BeforeAll
    void createSeedAndConnect() throws Exception {
        gadgets = ServiceDescriptors.fromAnnotated(entityType(), nameResolvers());
        fields = new LinkedHashMap<>();
        for (FieldMapping f : gadgets.fields()) {
            fields.put(f.logical().value(), f);
        }
        assertThat(gadgets.table().value()).isEqualTo("gadget");
        assertThat(fields.keySet()).containsExactlyInAnyOrder(
                "id", "name", "kind", "price", "stock", "active", "created_at");
        assertThat(fields.get("created_at").column().value()).isEqualTo("created_at");
        assertThat(fields.get("kind").caseSensitive()).as("kind must be @IntentField(caseSensitive = true)").isTrue();

        generateRows();
        executor = prepare(List.copyOf(data));
    }

    private void generateRows() {
        Random rnd = new Random(42);
        String alphabet = "abcdeXYZ 01_%'\\\"";
        for (long id = 1; id <= ROWS; id++) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", BigDecimal.valueOf(id));
            row.put("name", maybeNull(rnd, () -> {
                StringBuilder sb = new StringBuilder();
                for (int i = 0, n = 1 + rnd.nextInt(8); i < n; i++) {
                    sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
                }
                return sb.toString();
            }));
            row.put("kind", maybeNull(rnd, () -> GadgetKind.values()[rnd.nextInt(3)].name()));
            row.put("price", maybeNull(rnd, () -> BigDecimal.valueOf(rnd.nextInt(400_001) - 200_000, 2)));
            row.put("stock", maybeNull(rnd, () -> BigDecimal.valueOf(rnd.nextInt(41) - 20)));
            row.put("active", maybeNull(rnd, rnd::nextBoolean));
            row.put("created_at", maybeNull(rnd, () -> LocalDate.of(2020, 1, 1).plusDays(rnd.nextInt(365 * 8))
                    .atTime(rnd.nextInt(24), rnd.nextInt(60), rnd.nextInt(60))));
            data.add(row);
        }
    }

    @ParameterizedTest(name = "seed {0}")
    @MethodSource("seeds")
    void databaseAgreesWithReferenceEvaluation(long seed) {
        RandomQueries gen = new RandomQueries(seed, gadgets, pools(), "abcXYZ01 _%'\\\"", 4);
        List<String> columns = new ArrayList<>(fields.keySet());
        for (int i = 0; i < QUERIES_PER_SEED; i++) {
            AstQuery q = gen.query();
            String dsl = RandomQueries.print(q);
            IntentResponse<?> actual = pipelineAnswering(dsl).resolve(IntentRequests.of("q"), entityType());
            String ctx = entityType().getSimpleName() + " on " + target() + " seed " + seed + " #" + i + ": " + dsl;

            assertThat(actual.type()).as(ctx).isEqualTo(entityType());
            List<Map<String, Object>> matching = data.stream()
                    .filter(row -> Boolean.TRUE.equals(eval(q.filter(), row)))
                    .toList();
            List<List<Object>> expected = matching.stream().map(r -> project(r, columns)).toList();
            List<List<Object>> got = actual.items().stream().map(e -> row(e, columns)).toList();

            List<SortKey> sort = ValidatedQueries.of(gadgets, q).effectiveSort();
            List<List<Object>> ordered = new ArrayList<>(expected);
            ordered.sort(order(sort, columns));
            assertThat(got).as(ctx).containsExactlyElementsOf(page(ordered, q));
            assertThat(actual.trace().dsl()).as(ctx).isEqualTo(dsl);
            if (got.isEmpty()) {
                assertThat(actual.uri().value()).as(ctx).isEqualTo("/gadgets");
            } else {
                assertThat(actual.uri().value()).as(ctx)
                        .isEqualTo("/gadgets/" + ((BigDecimal) got.getFirst().get(columns.indexOf("id"))).toPlainString());
            }
        }
    }

    @ParameterizedTest(name = "seed {0}")
    @MethodSource("seeds")
    void aggregatesAgreeWithReferenceEvaluation(long seed) {
        RandomQueries gen = new RandomQueries(seed, gadgets, pools(), "abcXYZ01 _%'\\\"", 3);
        for (int i = 0; i < AGGREGATES_PER_SEED; i++) {
            AstQuery q = gen.aggregateQuery();
            Aggregate aggregate = Objects.requireNonNull(q.aggregate());
            String dsl = RandomQueries.print(q);
            String ctx = entityType().getSimpleName() + " on " + target() + " seed " + seed + " #" + i + ": " + dsl;
            IntentResponse<?> actual = pipelineAnswering(dsl).resolve(IntentRequests.of("q"), entityType());

            assertThat(actual.items()).as(ctx).isEmpty();
            Aggregation aggregation = Objects.requireNonNull(actual.aggregation(), ctx);
            List<List<Object>> got = aggregation.rows().stream().map(r -> {
                List<Object> row = new ArrayList<>();
                r.group().forEach(v -> row.add(normalize(v)));
                row.add(normalize(r.value()));
                return row;
            }).toList();

            List<List<Object>> expected = page(referenceAggregate(q, aggregate), q);
            assertThat(got).as(ctx).hasSameSizeAs(expected);
            for (int r = 0; r < got.size(); r++) {
                List<Object> g = got.get(r), e = expected.get(r);
                assertThat(g.subList(0, g.size() - 1)).as(ctx + " row " + r).isEqualTo(e.subList(0, e.size() - 1));
                Object gv = g.getLast(), ev = e.getLast();
                if (aggregate.function() == AggregateFunction.AVG && gv != null && ev != null) {
                    assertThat((BigDecimal) gv).as(ctx + " row " + r)
                            .isCloseTo((BigDecimal) ev, within(new BigDecimal("0.0001")));
                } else {
                    assertThat(gv).as(ctx + " row " + r).isEqualTo(ev);
                }
            }
        }
    }

    private List<List<Object>> referenceAggregate(AstQuery q, Aggregate aggregate) {
        List<String> groupBy = aggregate.groupBy().stream().map(LogicalFieldName::value).toList();
        Map<List<Object>, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (Map<String, Object> row : data) {
            if (Boolean.TRUE.equals(eval(q.filter(), row))) {
                List<Object> key = new ArrayList<>();
                groupBy.forEach(g -> key.add(normalize(row.get(g))));
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
            }
        }
        if (groupBy.isEmpty() && groups.isEmpty()) {
            groups.put(List.of(), List.of());
        }
        List<List<Object>> rows = new ArrayList<>();
        for (Map.Entry<List<Object>, List<Map<String, Object>>> group : groups.entrySet()) {
            List<Object> row = new ArrayList<>(group.getKey());
            row.add(normalize(aggregateValue(aggregate, group.getValue())));
            rows.add(row);
        }
        List<String> columns = new ArrayList<>(groupBy);
        columns.add(ValidatedQuery.valueField(aggregate).value());
        rows.sort(order(ValidatedQueries.of(gadgets, q).effectiveSort(), columns));
        return rows;
    }

    private static Object aggregateValue(Aggregate aggregate, List<Map<String, Object>> rows) {
        if (aggregate.function() == AggregateFunction.COUNT) {
            return BigDecimal.valueOf(rows.size());
        }
        List<Object> values = rows.stream().map(r -> normalize(r.get(aggregate.field().value())))
                .filter(Objects::nonNull).toList();
        if (values.isEmpty()) {
            return null;
        }
        return switch (aggregate.function()) {
            case SUM -> values.stream().map(BigDecimal.class::cast).reduce(BigDecimal.ZERO, BigDecimal::add);
            case AVG -> values.stream().map(BigDecimal.class::cast).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(values.size()), 10, java.math.RoundingMode.HALF_UP);
            case MIN -> values.stream().min(DifferentialTestBase::compareValues).orElseThrow();
            case MAX -> values.stream().max(DifferentialTestBase::compareValues).orElseThrow();
            case COUNT -> throw new IllegalStateException();
        };
    }

    private List<List<Object>> page(List<List<Object>> ordered, AstQuery q) {
        int skip = q.skip() == null ? 0 : q.skip().value();
        int limit = q.take() == null ? gadgets.maxTake().value() : q.take().value();
        return ordered.subList(Math.min(skip, ordered.size()), Math.min(ordered.size(), skip + limit));
    }

    private static Comparator<List<Object>> order(List<SortKey> keys, List<String> columns) {
        Comparator<List<Object>> order = (a, b) -> 0;
        for (SortKey k : keys) {
            int idx = columns.indexOf(k.field().value());
            Comparator<Object> values = DifferentialTestBase::compareValues;
            if (k.direction() == SortKey.Direction.DESC) {
                values = values.reversed();
            }
            order = order.thenComparing((List<Object> r) -> r.get(idx), Comparator.nullsLast(values));
        }
        return order;
    }

    private Boolean eval(FilterExpr e, Map<String, Object> row) {
        if (e == null) {
            return true;
        }
        return switch (e) {
            case And a -> {
                Boolean l = eval(a.left(), row), r = eval(a.right(), row);
                yield Boolean.FALSE.equals(l) || Boolean.FALSE.equals(r) ? Boolean.FALSE
                        : l == null || r == null ? null : Boolean.TRUE;
            }
            case Or o -> {
                Boolean l = eval(o.left(), row), r = eval(o.right(), row);
                yield Boolean.TRUE.equals(l) || Boolean.TRUE.equals(r) ? Boolean.TRUE
                        : l == null || r == null ? null : Boolean.FALSE;
            }
            case Not n -> {
                Boolean v = eval(n.inner(), row);
                yield v == null ? null : !v;
            }
            case IsNull n -> row.get(n.field().value()) == null;
            case IsNotNull n -> row.get(n.field().value()) != null;
            case In in -> {
                Object col = row.get(in.field().value());
                if (col == null) {
                    yield null;
                }
                FieldMapping fm = fields.get(in.field().value());
                yield in.values().stream().anyMatch(v -> fm.type() == FieldType.TIMESTAMP
                        ? Boolean.TRUE.equals(day(ComparisonOp.EQ, (LocalDateTime) col, ((TextLiteral) v).value()))
                        : compare(fm, col, v, true) == 0);
            }
            case Compare c -> {
                Object col = row.get(c.field().value());
                if (col == null) {
                    yield null;
                }
                FieldMapping fm = fields.get(c.field().value());
                Boolean day = fm.type() == FieldType.TIMESTAMP
                        ? day(c.op(), (LocalDateTime) col, ((TextLiteral) c.value()).value())
                        : null;
                if (day != null) {
                    yield day;
                }
                yield switch (c.op()) {
                    case CONTAINS -> lower(col).contains(lower(((TextLiteral) c.value()).value()));
                    case EQ -> compare(fm, col, c.value(), true) == 0;
                    case NE -> compare(fm, col, c.value(), true) != 0;
                    case LT -> compare(fm, col, c.value(), false) < 0;
                    case LE -> compare(fm, col, c.value(), false) <= 0;
                    case GT -> compare(fm, col, c.value(), false) > 0;
                    case GE -> compare(fm, col, c.value(), false) >= 0;
                };
            }
        };
    }

    private static Boolean day(ComparisonOp op, LocalDateTime at, String literal) {
        if (literal.length() != 10) {
            return dayless(op, at, LocalDateTime.parse(literal));
        }
        LocalDateTime start = LocalDate.parse(literal).atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        return switch (op) {
            case EQ -> !at.isBefore(start) && at.isBefore(end);
            case NE -> at.isBefore(start) || !at.isBefore(end);
            case LT -> at.isBefore(start);
            case GE -> !at.isBefore(start);
            case GT -> !at.isBefore(end);
            case LE -> at.isBefore(end);
            case CONTAINS -> throw new IllegalStateException();
        };
    }

    private static Boolean dayless(ComparisonOp op, LocalDateTime at, LocalDateTime value) {
        int c = at.compareTo(value);
        return switch (op) {
            case EQ -> c == 0;
            case NE -> c != 0;
            case LT -> c < 0;
            case LE -> c <= 0;
            case GT -> c > 0;
            case GE -> c >= 0;
            case CONTAINS -> throw new IllegalStateException();
        };
    }

    private static int compare(FieldMapping fm, Object col, DslLiteral v, boolean equality) {
        return switch (fm.type()) {
            case TEXT, ANY -> equality && !fm.caseSensitive()
                    ? lower(col).compareTo(lower(((TextLiteral) v).value()))
                    : ((String) col).compareTo(((TextLiteral) v).value());
            case NUMBER -> ((BigDecimal) col).compareTo(((NumberLiteral) v).value());
            case BOOLEAN -> Boolean.compare((Boolean) col, ((BoolLiteral) v).value());
            case TIMESTAMP -> ((LocalDateTime) col).compareTo(parseTimestamp(((TextLiteral) v).value()));
        };
    }

    private static LocalDateTime parseTimestamp(String s) {
        return s.length() == 10 ? LocalDate.parse(s).atStartOfDay() : LocalDateTime.parse(s);
    }

    private static String lower(Object s) {
        return ((String) s).toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compareValues(Object a, Object b) {
        return ((Comparable) a).compareTo(b);
    }

    private static List<Object> project(Map<String, Object> row, List<String> columns) {
        List<Object> out = new ArrayList<>(columns.size());
        for (String c : columns) {
            out.add(normalize(row.get(c)));
        }
        return out;
    }

    private List<Object> row(Object entity, List<String> columns) {
        List<Object> out = new ArrayList<>(columns.size());
        for (String c : columns) {
            out.add(normalize(readField(entity, Objects.requireNonNull(fields.get(c).property()))));
        }
        return out;
    }

    private static Object readField(Object entity, String name) {
        for (String accessor : new String[]{name, "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1)}) {
            try {
                java.lang.reflect.Method m = entity.getClass().getMethod(accessor);
                m.setAccessible(true);
                return m.invoke(entity);
            } catch (NoSuchMethodException e) {
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
        for (Class<?> t = entity.getClass(); t != null; t = t.getSuperclass()) {
            try {
                Field f = t.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(entity);
            } catch (NoSuchFieldException e) {
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalArgumentException(entity.getClass().getName() + " has no field " + name);
    }

    private static Object normalize(Object v) {
        return switch (v) {
            case null -> null;
            case BigDecimal bd -> bd.stripTrailingZeros();
            case Integer n -> BigDecimal.valueOf(n).stripTrailingZeros();
            case Long n -> BigDecimal.valueOf(n).stripTrailingZeros();
            case Enum<?> e -> e.name();
            case Timestamp t -> t.toLocalDateTime();
            case Instant t -> LocalDateTime.ofInstant(t, ZoneOffset.UTC);
            case OffsetDateTime t -> t.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
            default -> v;
        };
    }

    private Map<String, List<DslLiteral>> pools() {
        Map<String, List<DslLiteral>> pools = new LinkedHashMap<>();
        for (String field : fields.keySet()) {
            pools.put(field, data.stream().map(r -> r.get(field)).filter(Objects::nonNull).limit(30)
                    .map(DifferentialTestBase::literal).toList());
        }
        return pools;
    }

    private static DslLiteral literal(Object v) {
        return switch (v) {
            case String s -> new TextLiteral(s);
            case BigDecimal bd -> new NumberLiteral(bd);
            case Boolean b -> new BoolLiteral(b);
            case LocalDateTime t -> new TextLiteral(t.toString());
            default -> throw new IllegalArgumentException(v.getClass().getName());
        };
    }

    private static <T> T maybeNull(Random rnd, Supplier<T> value) {
        return rnd.nextInt(8) == 0 ? null : value.get();
    }

    private IntentPipeline pipelineAnswering(String dsl) {
        return IntentPipelines.create(
                TextNormalizers.create(), DslCaches.create(), (text, catalog) -> DslSources.of(dsl),
                DslParsers.create(), AstValidators.create(), sqlBuilder(), executor,
                UriBuilders.create(), ServiceCatalogs.of(gadgets));
    }
}
