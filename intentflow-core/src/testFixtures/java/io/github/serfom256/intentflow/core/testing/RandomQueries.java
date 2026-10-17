package io.github.serfom256.intentflow.core.testing;

import io.github.serfom256.intentflow.core.ast.Aggregate;
import io.github.serfom256.intentflow.core.ast.AggregateFunction;
import io.github.serfom256.intentflow.core.ast.And;
import io.github.serfom256.intentflow.core.ast.Arith;
import io.github.serfom256.intentflow.core.ast.ArithOp;
import io.github.serfom256.intentflow.core.ast.AstQuery;
import io.github.serfom256.intentflow.core.ast.BoolLiteral;
import io.github.serfom256.intentflow.core.ast.Call;
import io.github.serfom256.intentflow.core.ast.CastExpr;
import io.github.serfom256.intentflow.core.ast.CastType;
import io.github.serfom256.intentflow.core.ast.Compare;
import io.github.serfom256.intentflow.core.ast.ComparisonOp;
import io.github.serfom256.intentflow.core.ast.DslLiteral;
import io.github.serfom256.intentflow.core.ast.ExprCompare;
import io.github.serfom256.intentflow.core.ast.ExprFunction;
import io.github.serfom256.intentflow.core.ast.ExprIn;
import io.github.serfom256.intentflow.core.ast.ExprSortKey;
import io.github.serfom256.intentflow.core.ast.Expressions;
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
import io.github.serfom256.intentflow.core.ast.factories.AstQueries;
import io.github.serfom256.intentflow.core.ast.factories.Skips;
import io.github.serfom256.intentflow.core.ast.factories.Takes;
import io.github.serfom256.intentflow.core.catalog.FieldMapping;
import io.github.serfom256.intentflow.core.catalog.FieldType;
import io.github.serfom256.intentflow.core.catalog.LogicalFieldName;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import io.github.serfom256.intentflow.core.catalog.factories.LogicalFieldNames;
import io.github.serfom256.intentflow.core.catalog.factories.SortKeys;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class RandomQueries {

    public static final String NASTY_ASCII = "abcXYZ 019'\";%_\\-|~=<>()*/#!";

    private final Random rnd;
    private final ServiceDescriptor service;
    private final List<FieldMapping> queryable;
    private final Map<String, List<DslLiteral>> pools;
    private final String textAlphabet;
    private final int maxDepth;

    public RandomQueries(long seed, ServiceDescriptor service, Map<String, List<DslLiteral>> pools,
            String textAlphabet, int maxDepth) {
        this.rnd = new Random(seed);
        this.service = service;
        this.queryable = service.fields().stream().filter(FieldMapping::queryable).toList();
        this.pools = pools;
        this.textAlphabet = textAlphabet;
        this.maxDepth = maxDepth;
    }

    public RandomQueries(long seed, ServiceDescriptor service) {
        this(seed, service, Map.of(), NASTY_ASCII + "ßéİ漢字", 4);
    }

    public AstQuery query() {
        FilterExpr filter = rnd.nextInt(5) == 0 ? null : filter(rnd.nextInt(maxDepth + 1));
        return AstQueries.of(service.name(), filter, sort(),
                rnd.nextInt(3) == 0 ? Skips.of(rnd.nextInt(5)) : null,
                rnd.nextInt(3) == 0 ? Takes.of(1 + rnd.nextInt(service.maxTake().value())) : null,
                null, rnd.nextInt(4) == 0 ? top() : null);
    }

    private TopN top() {
        List<FieldMapping> partitionable = queryable.stream()
                .filter(f -> f.type() != FieldType.TEXT && f.type() != FieldType.ANY).toList();
        List<FieldMapping> times = typed(FieldType.TIMESTAMP);
        List<ValueExpr> partition = new ArrayList<>();
        for (int i = 0, n = rnd.nextInt(3); i < n; i++) {
            ValueExpr part = rnd.nextInt(3) == 0 && !times.isEmpty()
                    ? new Call(ExprFunction.YEAR, List.of(field(times)))
                    : new FieldRef(pick(partitionable).logical());
            if (!partition.contains(part)) {
                partition.add(part);
            }
        }
        List<SortKey> order = sort();
        if (order.isEmpty()) {
            order = List.of(SortKeys.of(pick(queryable).logical(),
                    rnd.nextBoolean() ? SortKey.Direction.ASC : SortKey.Direction.DESC));
        }
        return new TopN(1 + rnd.nextInt(3), partition, order);
    }

    public AstQuery aggregateQuery() {
        List<FieldMapping> groupable = queryable.stream().filter(f -> f.type() != FieldType.ANY).toList();
        List<FieldMapping> numbers = typed(FieldType.NUMBER);
        List<FieldMapping> ordered = queryable.stream()
                .filter(f -> f.type() == FieldType.NUMBER || f.type() == FieldType.TIMESTAMP).toList();
        List<ValueExpr> groupBy = new ArrayList<>();
        for (int i = 0, n = rnd.nextInt(3); i < n; i++) {
            ValueExpr group = rnd.nextInt(3) == 0 ? groupExpression() : null;
            if (group == null) {
                group = new FieldRef(pick(groupable).logical());
            }
            if (!groupBy.contains(group)) {
                groupBy.add(group);
            }
        }
        AggregateFunction function = pick(List.of(AggregateFunction.values()));
        ValueExpr argument = switch (function) {
            case COUNT -> null;
            case SUM, AVG -> rnd.nextInt(3) == 0 ? numberExpression(numbers) : new FieldRef(pick(numbers).logical());
            case MIN, MAX -> new FieldRef(pick(ordered).logical());
        };
        List<SortKey> sort = new ArrayList<>();
        if (!groupBy.isEmpty() && rnd.nextBoolean()) {
            SortKey.Direction direction = rnd.nextBoolean() ? SortKey.Direction.ASC : SortKey.Direction.DESC;
            if (rnd.nextBoolean()) {
                sort.add(SortKeys.of(LogicalFieldNames.of(function.keyword()), direction));
            } else {
                ValueExpr group = pick(groupBy);
                sort.add(group instanceof FieldRef f ? SortKeys.of(f.field(), direction)
                        : new ExprSortKey(group, direction));
            }
        }
        FilterExpr filter = rnd.nextInt(3) == 0 ? null : filter(rnd.nextInt(maxDepth + 1));
        return AstQueries.of(service.name(), filter, sort,
                rnd.nextInt(4) == 0 ? Skips.of(rnd.nextInt(3)) : null,
                rnd.nextInt(3) == 0 ? Takes.of(1 + rnd.nextInt(5)) : null,
                new Aggregate(function, argument, groupBy));
    }

    private ValueExpr groupExpression() {
        List<FieldMapping> numbers = typed(FieldType.NUMBER);
        List<FieldMapping> texts = typed(FieldType.TEXT);
        List<FieldMapping> times = typed(FieldType.TIMESTAMP);
        List<Supplier<ValueExpr>> options = new ArrayList<>();
        if (!times.isEmpty()) {
            options.add(() -> new Call(ExprFunction.YEAR, List.of(field(times))));
            options.add(() -> new Call(ExprFunction.MONTH, List.of(field(times))));
        }
        if (!numbers.isEmpty()) {
            options.add(() -> new Call(ExprFunction.ABS, List.of(field(numbers))));
        }
        if (!texts.isEmpty()) {
            options.add(() -> new Call(ExprFunction.COALESCE, List.of(field(texts), text("NONE"))));
        }
        return options.isEmpty() ? null : pick(options).get();
    }

    private ValueExpr numberExpression(List<FieldMapping> numbers) {
        return switch (rnd.nextInt(8)) {
            case 0 ->
                new Call(ExprFunction.COALESCE, List.of(field(numbers), new LiteralExpr(new NumberLiteral(number()))));
            case 1 -> new Call(ExprFunction.ROUND, List.of(field(numbers)));
            case 2 -> new Call(ExprFunction.ROUND,
                    List.of(field(numbers), new LiteralExpr(new NumberLiteral(BigDecimal.ONE))));
            case 3 -> new Call(ExprFunction.ABS, List.of(field(numbers)));
            case 4 -> new Arith(ArithOp.ADD, field(numbers), field(numbers));
            case 5 -> new Arith(ArithOp.MULTIPLY, field(numbers),
                    new LiteralExpr(new NumberLiteral(BigDecimal.valueOf(rnd.nextInt(7) - 3, rnd.nextInt(2)))));
            case 6 -> new Arith(ArithOp.SUBTRACT, field(numbers), new LiteralExpr(new NumberLiteral(number())));
            default -> new CastExpr(field(numbers), CastType.INTEGER);
        };
    }

    public FilterExpr expressionLeaf() {
        List<FieldMapping> numbers = typed(FieldType.NUMBER);
        List<FieldMapping> texts = typed(FieldType.TEXT);
        List<FieldMapping> times = typed(FieldType.TIMESTAMP);
        List<Supplier<FilterExpr>> options = new ArrayList<>();
        if (!numbers.isEmpty()) {
            options.add(() -> new ExprCompare(numberExpression(numbers), orderOp(),
                    new LiteralExpr(new NumberLiteral(number()))));
            options.add(() -> new ExprCompare(field(numbers), orderOp(), field(numbers)));
            options.add(() -> new ExprCompare(new CastExpr(field(numbers), CastType.TEXT), equalityOp(),
                    text(String.valueOf(rnd.nextInt(41) - 20))));
            options.add(() -> new ExprCompare(new Call(ExprFunction.COALESCE, List.of(field(numbers), field(numbers))),
                    equalityOp(), new LiteralExpr(new NullLiteral())));
        }
        if (!texts.isEmpty()) {
            options.add(() -> new ExprCompare(new Call(ExprFunction.LENGTH, List.of(field(texts))), orderOp(),
                    new LiteralExpr(new NumberLiteral(BigDecimal.valueOf(rnd.nextInt(9))))));
            options.add(
                    () -> new ExprCompare(new Call(ExprFunction.LOWER, List.of(field(texts))), ComparisonOp.CONTAINS,
                            text(text())));
            options.add(() -> {
                FieldMapping f = pick(texts);
                return new ExprCompare(
                        new Call(ExprFunction.COALESCE, List.of(new FieldRef(f.logical()), text("none"))),
                        equalityOp(), new LiteralExpr(value(f)));
            });
        }
        if (!times.isEmpty()) {
            options.add(() -> new ExprCompare(new Call(ExprFunction.YEAR, List.of(field(times))), orderOp(),
                    new LiteralExpr(new NumberLiteral(BigDecimal.valueOf(2019 + rnd.nextInt(10))))));
            options.add(() -> new ExprCompare(new Call(ExprFunction.HOUR, List.of(field(times))), orderOp(),
                    new LiteralExpr(new NumberLiteral(BigDecimal.valueOf(rnd.nextInt(24))))));
            options.add(() -> {
                List<DslLiteral> months = new ArrayList<>();
                for (int i = 0, n = 1 + rnd.nextInt(4); i < n; i++) {
                    months.add(new NumberLiteral(BigDecimal.valueOf(1 + rnd.nextInt(12))));
                }
                return new ExprIn(new Call(ExprFunction.MONTH, List.of(field(times))), months);
            });
            options.add(() -> new ExprCompare(new Call(ExprFunction.DATE_TRUNC, List.of(text("month"), field(times))),
                    orderOp(), text(timestamp())));
            options.add(() -> new ExprCompare(new CastExpr(field(times), CastType.DATE), orderOp(),
                    text(timestamp().substring(0, 10))));
        }
        return options.isEmpty() ? fieldLeaf() : pick(options).get();
    }

    private List<FieldMapping> typed(FieldType type) {
        return queryable.stream().filter(f -> f.type() == type).toList();
    }

    private FieldRef field(List<FieldMapping> fields) {
        return new FieldRef(pick(fields).logical());
    }

    private static LiteralExpr text(String value) {
        return new LiteralExpr(new TextLiteral(value));
    }

    private ComparisonOp orderOp() {
        return pick(List.of(ComparisonOp.EQ, ComparisonOp.NE, ComparisonOp.LT, ComparisonOp.LE, ComparisonOp.GT,
                ComparisonOp.GE));
    }

    private ComparisonOp equalityOp() {
        return rnd.nextBoolean() ? ComparisonOp.EQ : ComparisonOp.NE;
    }

    public FilterExpr filter(int depth) {
        if (depth == 0) {
            return leaf();
        }
        return switch (rnd.nextInt(4)) {
            case 0 -> new And(filter(depth - 1), filter(rnd.nextInt(depth)));
            case 1 -> new Or(filter(rnd.nextInt(depth)), filter(depth - 1));
            case 2 -> new Not(filter(depth - 1));
            default -> leaf();
        };
    }

    public FilterExpr leaf() {
        if (rnd.nextInt(5) == 0) {
            return expressionLeaf();
        }
        return fieldLeaf();
    }

    private FilterExpr fieldLeaf() {
        FieldMapping f = pick(queryable);
        LogicalFieldName name = f.logical();
        int kind = rnd.nextInt(10);
        if (kind == 0) {
            return new IsNull(name);
        }
        if (kind == 1) {
            return new IsNotNull(name);
        }
        if (kind == 2) {
            List<DslLiteral> values = new ArrayList<>();
            int n = 1 + rnd.nextInt(4);
            for (int i = 0; i < n; i++) {
                values.add(value(f));
            }
            return new In(name, values);
        }
        return new Compare(name, op(f), value(f));
    }

    private ComparisonOp op(FieldMapping f) {
        return switch (f.type()) {
            case BOOLEAN -> rnd.nextBoolean() ? ComparisonOp.EQ : ComparisonOp.NE;
            case TEXT, ANY -> pick(f.allowedValues().isEmpty()
                    ? List.of(ComparisonOp.values())
                    : List.of(ComparisonOp.EQ, ComparisonOp.NE, ComparisonOp.CONTAINS));
            case NUMBER, TIMESTAMP -> pick(List.of(ComparisonOp.EQ, ComparisonOp.NE, ComparisonOp.LT,
                    ComparisonOp.LE, ComparisonOp.GT, ComparisonOp.GE));
        };
    }

    public DslLiteral value(FieldMapping f) {
        List<DslLiteral> pool = pools.getOrDefault(f.logical().value(), List.of());
        if (!pool.isEmpty() && rnd.nextInt(10) < 7) {
            DslLiteral v = pick(pool);
            if (v instanceof TextLiteral t && f.type() == FieldType.TEXT && f.allowedValues().isEmpty()
                    && rnd.nextBoolean()) {
                return new TextLiteral(randomCase(t.value()));
            }
            return v;
        }
        return switch (f.type()) {
            case TEXT, ANY -> f.allowedValues().isEmpty()
                    ? new TextLiteral(text())
                    : new TextLiteral(randomCase(pick(f.allowedValues())));
            case NUMBER -> new NumberLiteral(number());
            case BOOLEAN -> new BoolLiteral(rnd.nextBoolean());
            case TIMESTAMP -> new TextLiteral(timestamp());
        };
    }

    private List<SortKey> sort() {
        int n = rnd.nextInt(3);
        List<SortKey> keys = new ArrayList<>();
        List<FieldMapping> numbers = typed(FieldType.NUMBER);
        List<FieldMapping> times = typed(FieldType.TIMESTAMP);
        for (int i = 0; i < n; i++) {
            SortKey.Direction direction = rnd.nextBoolean() ? SortKey.Direction.ASC : SortKey.Direction.DESC;
            if (rnd.nextInt(4) == 0 && !numbers.isEmpty()) {
                keys.add(new ExprSortKey(numberExpression(numbers), direction));
            } else if (rnd.nextInt(6) == 0 && !times.isEmpty()) {
                keys.add(new ExprSortKey(new Call(ExprFunction.YEAR, List.of(field(times))), direction));
            } else {
                keys.add(SortKeys.of(pick(queryable).logical(), direction));
            }
        }
        return keys;
    }

    private String text() {
        int len = rnd.nextInt(6);
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.appendCodePoint(textAlphabet.codePointAt(
                    textAlphabet.offsetByCodePoints(0,
                            rnd.nextInt(textAlphabet.codePointCount(0, textAlphabet.length())))));
        }
        return sb.toString();
    }

    private BigDecimal number() {
        long unscaled = rnd.nextInt(4001) - 2000;
        return BigDecimal.valueOf(unscaled, rnd.nextInt(3));
    }

    private String timestamp() {
        LocalDate day = LocalDate.of(2020, 1, 1).plusDays(rnd.nextInt(365 * 8));
        return rnd.nextBoolean()
                ? day.toString()
                : day + "T%02d:%02d:%02d".formatted(rnd.nextInt(24), rnd.nextInt(60), rnd.nextInt(60));
    }

    private String randomCase(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            sb.append(c < 128 && rnd.nextBoolean() ? Character.toUpperCase(c) : Character.toLowerCase(c));
        }
        return sb.toString();
    }

    private <T> T pick(List<T> list) {
        return list.get(rnd.nextInt(list.size()));
    }

    public static String print(AstQuery q) {
        StringBuilder sb = new StringBuilder(q.service().value());
        if (q.filter() != null) {
            sb.append(" | filter ").append(print(q.filter()));
        }
        if (q.aggregate() != null) {
            Aggregate a = q.aggregate();
            if (!a.groupBy().isEmpty()) {
                sb.append(" | group ").append(String.join(", ", a.groupNames()));
            }
            sb.append(" | ").append(a.function().keyword());
            if (a.argument() != null) {
                sb.append(' ').append(Expressions.print(a.argument()));
            }
        }
        if (q.top() != null) {
            TopN top = q.top();
            sb.append(" | top ").append(top.count());
            if (!top.partition().isEmpty()) {
                sb.append(" per ").append(top.partition().stream().map(Expressions::print)
                        .collect(Collectors.joining(", ")));
            }
            sb.append(" by ").append(sortKeys(top.order()));
        }
        if (!q.sort().isEmpty()) {
            sb.append(" | sort ").append(sortKeys(q.sort()));
        }
        if (q.skip() != null) {
            sb.append(" | skip ").append(q.skip().value());
        }
        if (q.take() != null) {
            sb.append(" | take ").append(q.take().value());
        }
        return sb.toString();
    }

    private static String sortKeys(List<SortKey> keys) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            SortKey k = keys.get(i);
            if (i > 0)
                sb.append(", ");
            sb.append(k.field().value()).append(k.direction() == SortKey.Direction.DESC ? " desc" : " asc");
        }
        return sb.toString();
    }

    public static String print(FilterExpr e) {
        return switch (e) {
            case And a -> "(" + print(a.left()) + " and " + print(a.right()) + ")";
            case Or o -> "(" + print(o.left()) + " or " + print(o.right()) + ")";
            case Not n -> "not (" + print(n.inner()) + ")";
            case Compare c -> c.field().value() + " " + symbol(c.op()) + " " + print(c.value());
            case In in -> in.field().value() + " in ("
                    + String.join(", ", in.values().stream().map(RandomQueries::print).toList()) + ")";
            case IsNull n -> n.field().value() + " == null";
            case IsNotNull n -> n.field().value() + " != null";
            case ExprCompare c ->
                Expressions.print(c.left()) + " " + symbol(c.op()) + " " + Expressions.print(c.right());
            case ExprIn in -> Expressions.print(in.value()) + " in ("
                    + String.join(", ", in.values().stream().map(RandomQueries::print).toList()) + ")";
        };
    }

    public static String print(DslLiteral v) {
        return switch (v) {
            case TextLiteral t -> "\"" + t.value()
                    .replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\t", "\\t") + "\"";
            case NumberLiteral n -> n.value().toPlainString();
            case BoolLiteral b -> Boolean.toString(b.value());
            case NullLiteral n -> "null";
        };
    }

    private static String symbol(ComparisonOp op) {
        return switch (op) {
            case EQ -> "==";
            case NE -> "!=";
            case LT -> "<";
            case LE -> "<=";
            case GT -> ">";
            case GE -> ">=";
            case CONTAINS -> "~";
        };
    }
}
