package io.github.serfom256.intentflow.core.testing;

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
import io.github.serfom256.intentflow.core.ast.NullLiteral;
import io.github.serfom256.intentflow.core.ast.NumberLiteral;
import io.github.serfom256.intentflow.core.ast.Or;
import io.github.serfom256.intentflow.core.ast.TextLiteral;
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
                rnd.nextInt(3) == 0 ? Takes.of(1 + rnd.nextInt(service.maxTake().value())) : null);
    }

    public AstQuery aggregateQuery() {
        List<FieldMapping> groupable = queryable.stream().filter(f -> f.type() != FieldType.ANY).toList();
        List<FieldMapping> numbers = queryable.stream().filter(f -> f.type() == FieldType.NUMBER).toList();
        List<FieldMapping> ordered = queryable.stream()
                .filter(f -> f.type() == FieldType.NUMBER || f.type() == FieldType.TIMESTAMP).toList();
        List<LogicalFieldName> groupBy = new ArrayList<>();
        for (int i = 0, n = rnd.nextInt(3); i < n; i++) {
            LogicalFieldName f = pick(groupable).logical();
            if (!groupBy.contains(f)) {
                groupBy.add(f);
            }
        }
        AggregateFunction function = pick(List.of(AggregateFunction.values()));
        LogicalFieldName field = switch (function) {
            case COUNT -> null;
            case SUM, AVG -> pick(numbers).logical();
            case MIN, MAX -> pick(ordered).logical();
        };
        List<SortKey> sort = new ArrayList<>();
        if (!groupBy.isEmpty() && rnd.nextBoolean()) {
            List<LogicalFieldName> sortable = new ArrayList<>(groupBy);
            sortable.add(LogicalFieldNames.of(function.keyword()));
            sort.add(SortKeys.of(pick(sortable), rnd.nextBoolean() ? SortKey.Direction.ASC : SortKey.Direction.DESC));
        }
        FilterExpr filter = rnd.nextInt(3) == 0 ? null : filter(rnd.nextInt(maxDepth + 1));
        return AstQueries.of(service.name(), filter, sort,
                rnd.nextInt(4) == 0 ? Skips.of(rnd.nextInt(3)) : null,
                rnd.nextInt(3) == 0 ? Takes.of(1 + rnd.nextInt(5)) : null,
                new Aggregate(function, field, groupBy));
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
        for (int i = 0; i < n; i++) {
            keys.add(SortKeys.of(pick(queryable).logical(),
                    rnd.nextBoolean() ? SortKey.Direction.ASC : SortKey.Direction.DESC));
        }
        return keys;
    }

    private String text() {
        int len = rnd.nextInt(6);
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.appendCodePoint(textAlphabet.codePointAt(
                    textAlphabet.offsetByCodePoints(0, rnd.nextInt(textAlphabet.codePointCount(0, textAlphabet.length())))));
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
                sb.append(" | group ").append(String.join(", ", a.groupBy().stream().map(LogicalFieldName::value).toList()));
            }
            sb.append(" | ").append(a.function().keyword());
            if (a.field() != null) {
                sb.append(' ').append(a.field().value());
            }
        }
        if (!q.sort().isEmpty()) {
            sb.append(" | sort ");
            for (int i = 0; i < q.sort().size(); i++) {
                SortKey k = q.sort().get(i);
                if (i > 0) sb.append(", ");
                sb.append(k.field().value()).append(k.direction() == SortKey.Direction.DESC ? " desc" : " asc");
            }
        }
        if (q.skip() != null) {
            sb.append(" | skip ").append(q.skip().value());
        }
        if (q.take() != null) {
            sb.append(" | take ").append(q.take().value());
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
