package io.github.serfom256.intentflow.core.testing;

import io.github.serfom256.intentflow.core.api.Aggregation;
import io.github.serfom256.intentflow.core.api.IntentResponse;
import io.github.serfom256.intentflow.core.api.factories.IntentRequests;
import io.github.serfom256.intentflow.core.ast.Aggregate;
import io.github.serfom256.intentflow.core.ast.AggregateFunction;
import io.github.serfom256.intentflow.core.ast.And;
import io.github.serfom256.intentflow.core.ast.Arith;
import io.github.serfom256.intentflow.core.ast.AstQuery;
import io.github.serfom256.intentflow.core.ast.BoolLiteral;
import io.github.serfom256.intentflow.core.ast.Call;
import io.github.serfom256.intentflow.core.ast.CastExpr;
import io.github.serfom256.intentflow.core.ast.Compare;
import io.github.serfom256.intentflow.core.ast.ComparisonOp;
import io.github.serfom256.intentflow.core.ast.DslLiteral;
import io.github.serfom256.intentflow.core.ast.ExprCompare;
import io.github.serfom256.intentflow.core.ast.ExprFunction;
import io.github.serfom256.intentflow.core.ast.ExprIn;
import io.github.serfom256.intentflow.core.ast.ExprSortKey;
import io.github.serfom256.intentflow.core.ast.FieldRef;
import io.github.serfom256.intentflow.core.ast.FilterExpr;
import io.github.serfom256.intentflow.core.ast.In;
import io.github.serfom256.intentflow.core.ast.IsNotNull;
import io.github.serfom256.intentflow.core.ast.IsNull;
import io.github.serfom256.intentflow.core.ast.LiteralExpr;
import io.github.serfom256.intentflow.core.ast.Not;
import io.github.serfom256.intentflow.core.ast.NullLiteral;
import io.github.serfom256.intentflow.core.ast.NumberLiteral;
import io.github.serfom256.intentflow.core.ast.Or;
import io.github.serfom256.intentflow.core.ast.TextLiteral;
import io.github.serfom256.intentflow.core.ast.TopN;
import io.github.serfom256.intentflow.core.ast.ValueExpr;
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
import io.github.serfom256.intentflow.core.pipeline.factories.IntentPipelines;
import io.github.serfom256.intentflow.core.pipeline.factories.ValidatedQueries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;
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
            List<Map<String, Object>> matching = top(q, data.stream()
                    .filter(row -> Boolean.TRUE.equals(eval(q.filter(), row)))
                    .toList());
            List<List<Object>> got = actual.items().stream().map(e -> row(e, columns)).toList();

            List<SortKey> sort = ValidatedQueries.of(gadgets, q).effectiveSort();
            List<Map<String, Object>> sorted = new ArrayList<>(matching);
            sorted.sort(rowOrder(sort));
            List<List<Object>> ordered = sorted.stream().map(r -> project(r, columns)).toList();
            assertThat(got).as(ctx).containsExactlyElementsOf(page(ordered, q));
            assertThat(actual.trace().dsl()).as(ctx).isEqualTo(dsl);
            if (got.isEmpty()) {
                assertThat(actual.uri().value()).as(ctx).isEqualTo("/gadgets");
            } else {
                assertThat(actual.uri().value()).as(ctx)
                        .isEqualTo(
                                "/gadgets/" + ((BigDecimal) got.getFirst().get(columns.indexOf("id"))).toPlainString());
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
        List<String> groupBy = aggregate.groupNames();
        Map<List<Object>, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (Map<String, Object> row : data) {
            if (Boolean.TRUE.equals(eval(q.filter(), row))) {
                List<Object> key = new ArrayList<>();
                aggregate.groupBy().forEach(g -> key.add(normalize(value(g, row))));
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

    private Object aggregateValue(Aggregate aggregate, List<Map<String, Object>> rows) {
        if (aggregate.function() == AggregateFunction.COUNT) {
            return BigDecimal.valueOf(rows.size());
        }
        List<Object> values = rows.stream().map(r -> normalize(value(aggregate.argument(), r)))
                .filter(Objects::nonNull).toList();
        if (values.isEmpty()) {
            return null;
        }
        return switch (aggregate.function()) {
            case SUM -> values.stream().map(BigDecimal.class::cast).reduce(BigDecimal.ZERO, BigDecimal::add);
            case AVG -> values.stream().map(BigDecimal.class::cast).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(values.size()), 10, RoundingMode.HALF_UP);
            case MIN -> values.stream().min(DifferentialTestBase::compareValues).orElseThrow();
            case MAX -> values.stream().max(DifferentialTestBase::compareValues).orElseThrow();
            case COUNT -> throw new IllegalStateException();
        };
    }

    private List<Map<String, Object>> top(AstQuery q, List<Map<String, Object>> rows) {
        TopN top = q.top();
        if (top == null) {
            return rows;
        }
        Comparator<Map<String, Object>> order = rowOrder(ValidatedQueries.of(gadgets, q).topOrder());
        Map<List<Object>, List<Map<String, Object>>> partitions = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            List<Object> key = new ArrayList<>();
            top.partition().forEach(p -> key.add(normalize(value(p, row))));
            partitions.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }
        List<Map<String, Object>> kept = new ArrayList<>();
        for (List<Map<String, Object>> partition : partitions.values()) {
            partition.sort(order);
            kept.addAll(partition.subList(0, Math.min(top.count(), partition.size())));
        }
        return kept;
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

    private Comparator<Map<String, Object>> rowOrder(List<SortKey> keys) {
        Comparator<Map<String, Object>> order = (a, b) -> 0;
        for (SortKey k : keys) {
            Function<Map<String, Object>, Object> read = k instanceof ExprSortKey e
                    ? r -> normalize(value(e.expression(), r))
                    : r -> normalize(r.get(k.field().value()));
            Comparator<Object> values = DifferentialTestBase::compareValues;
            if (k.direction() == SortKey.Direction.DESC) {
                values = values.reversed();
            }
            order = order.thenComparing(read, Comparator.nullsLast(values));
        }
        return order;
    }

    private Object value(ValueExpr expr, Map<String, Object> row) {
        return switch (expr) {
            case FieldRef f -> row.get(f.field().value());
            case LiteralExpr l -> switch (l.value()) {
                case TextLiteral t -> t.value();
                case NumberLiteral n -> n.value();
                case BoolLiteral b -> b.value();
                case NullLiteral n -> null;
            };
            case Arith a -> {
                Object left = value(a.left(), row), right = value(a.right(), row);
                if (left == null || right == null) {
                    yield null;
                }
                BigDecimal l = (BigDecimal) left, r = (BigDecimal) right;
                yield switch (a.op()) {
                    case ADD -> l.add(r);
                    case SUBTRACT -> l.subtract(r);
                    case MULTIPLY -> l.multiply(r);
                    case DIVIDE -> r.signum() == 0 ? null : l.divide(r, 20, RoundingMode.HALF_UP);
                };
            }
            case CastExpr c -> {
                Object v = value(c.value(), row);
                if (v == null) {
                    yield null;
                }
                yield switch (c.type()) {
                    case NUMBER -> v instanceof BigDecimal n ? n : new BigDecimal(v.toString().strip());
                    case INTEGER -> (v instanceof BigDecimal n ? n : new BigDecimal(v.toString().strip()))
                            .setScale(0, RoundingMode.HALF_UP);
                    case TEXT -> v instanceof BigDecimal n ? n.toPlainString() : v.toString();
                    case TIMESTAMP -> v instanceof String s ? parseTimestamp(s) : v;
                    case DATE -> v instanceof String s ? LocalDate.parse(s.substring(0, 10))
                            : ((LocalDateTime) v).toLocalDate();
                };
            }
            case Call c -> call(c, row);
        };
    }

    private Object call(Call c, Map<String, Object> row) {
        List<ValueExpr> args = c.args();
        if (c.function() == ExprFunction.COALESCE) {
            boolean temporal = args.stream().anyMatch(a -> a instanceof FieldRef f
                    && fields.get(f.field().value()).type() == FieldType.TIMESTAMP);
            for (ValueExpr arg : args) {
                Object v = value(arg, row);
                if (v != null) {
                    return temporal && v instanceof String s ? parseTimestamp(s) : v;
                }
            }
            return null;
        }
        Object v = value(args.getLast(), row);
        if (v == null) {
            return null;
        }
        return switch (c.function()) {
            case YEAR -> BigDecimal.valueOf(((LocalDateTime) v).getYear());
            case MONTH -> BigDecimal.valueOf(((LocalDateTime) v).getMonthValue());
            case DAY -> BigDecimal.valueOf(((LocalDateTime) v).getDayOfMonth());
            case HOUR -> BigDecimal.valueOf(((LocalDateTime) v).getHour());
            case DATE_TRUNC -> {
                LocalDateTime t = (LocalDateTime) v;
                yield switch (((String) value(args.getFirst(), row)).toLowerCase(Locale.ROOT)) {
                    case "year" -> t.withDayOfYear(1).truncatedTo(ChronoUnit.DAYS);
                    case "month" -> t.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
                    case "day" -> t.truncatedTo(ChronoUnit.DAYS);
                    default -> t.truncatedTo(ChronoUnit.HOURS);
                };
            }
            case LENGTH -> BigDecimal.valueOf(((String) v).codePointCount(0, ((String) v).length()));
            case LOWER -> ((String) v).toLowerCase(Locale.ROOT);
            case UPPER -> ((String) v).toUpperCase(Locale.ROOT);
            case ABS -> ((BigDecimal) v).abs();
            case ROUND -> {
                Object first = value(args.getFirst(), row);
                yield first == null ? null
                        : ((BigDecimal) first).setScale(args.size() == 2
                                ? ((NumberLiteral) ((LiteralExpr) args.get(1)).value()).value().intValueExact()
                                : 0,
                                RoundingMode.HALF_UP);
            }
            case COALESCE -> throw new IllegalStateException();
        };
    }

    private Boolean exprCompare(ValueExpr leftExpr, ComparisonOp op, ValueExpr rightExpr, Map<String, Object> row) {
        Object left = value(leftExpr, row);
        if (rightExpr instanceof LiteralExpr l && l.value() instanceof NullLiteral) {
            return op == ComparisonOp.EQ ? left == null : left != null;
        }
        Object right = value(rightExpr, row);
        if (left == null && op == ComparisonOp.NE) {
            return true;
        }
        if (left == null || right == null) {
            return null;
        }
        if (op == ComparisonOp.CONTAINS) {
            return lower(left).contains(lower(right));
        }
        if (left instanceof LocalDateTime at && right instanceof String s) {
            if (s.length() == 10) {
                return day(op, at, s);
            }
            right = LocalDateTime.parse(s);
        } else if (left instanceof LocalDate && right instanceof String s) {
            right = LocalDate.parse(s.substring(0, 10));
        }
        int c = left instanceof String a && right instanceof String b
                && (op == ComparisonOp.EQ || op == ComparisonOp.NE)
                        ? lower(a).compareTo(lower(b))
                        : compareValues(left, right);
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
                if (nullOperand(n.inner(), row)) {
                    yield true;
                }
                Boolean v = eval(n.inner(), row);
                yield v == null ? null : !v;
            }
            case ExprCompare c -> exprCompare(c.left(), c.op(), c.right(), row);
            case ExprIn in -> {
                boolean unknown = false;
                Boolean result = Boolean.FALSE;
                for (DslLiteral v : in.values()) {
                    Boolean match = exprCompare(in.value(), ComparisonOp.EQ, new LiteralExpr(v), row);
                    if (Boolean.TRUE.equals(match)) {
                        result = Boolean.TRUE;
                        break;
                    }
                    unknown |= match == null;
                }
                yield Boolean.TRUE.equals(result) ? Boolean.TRUE : unknown ? null : Boolean.FALSE;
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
                    yield c.op() == ComparisonOp.NE ? Boolean.TRUE : null;
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

    private boolean nullOperand(FilterExpr inner, Map<String, Object> row) {
        return switch (inner) {
            case Compare c when c.op() != ComparisonOp.NE -> row.get(c.field().value()) == null;
            case In i -> row.get(i.field().value()) == null;
            case ExprCompare c when c.op() != ComparisonOp.NE
                    && !(c.right() instanceof LiteralExpr l && l.value() instanceof NullLiteral) ->
                value(c.left(), row) == null;
            case ExprIn i -> value(i.value(), row) == null;
            default -> false;
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

    @SuppressWarnings({ "unchecked", "rawtypes" })
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
        for (String accessor : new String[] { name,
                "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1) }) {
            try {
                Method m = entity.getClass().getMethod(accessor);
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
        return IntentPipelines.builder()
                .llm((text, catalog) -> DslSources.of(dsl))
                .sqlBuilder(sqlBuilder())
                .executor(executor)
                .catalog(ServiceCatalogs.of(gadgets))
                .build();
    }
}
