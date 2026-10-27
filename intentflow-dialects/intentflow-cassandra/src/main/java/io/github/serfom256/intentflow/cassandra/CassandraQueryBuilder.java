package io.github.serfom256.intentflow.cassandra;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.metadata.schema.ColumnMetadata;
import com.datastax.oss.driver.api.core.metadata.schema.TableMetadata;
import io.github.serfom256.intentflow.core.ErrorCode;
import io.github.serfom256.intentflow.core.ast.Aggregate;
import io.github.serfom256.intentflow.core.ast.And;
import io.github.serfom256.intentflow.core.ast.AstQuery;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.Compare;
import io.github.serfom256.intentflow.core.ast.ComparisonOp;
import io.github.serfom256.intentflow.core.ast.DslLiteral;
import io.github.serfom256.intentflow.core.ast.FilterExpr;
import io.github.serfom256.intentflow.core.ast.Take;
import io.github.serfom256.intentflow.core.ast.TextLiteral;
import io.github.serfom256.intentflow.core.ast.TopN;
import io.github.serfom256.intentflow.core.ast.ValueExpr;
import io.github.serfom256.intentflow.core.catalog.FieldMapping;
import io.github.serfom256.intentflow.core.catalog.FieldType;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.pipeline.SqlBuilder;
import io.github.serfom256.intentflow.core.pipeline.ValidatedQuery;
import io.github.serfom256.intentflow.core.pipeline.ValidationException;
import org.jspecify.annotations.Nullable;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public final class CassandraQueryBuilder implements SqlBuilder {

    private final CqlSession session;
    private final ZoneId zone;

    public CassandraQueryBuilder(CqlSession session) {
        this(session, ZoneId.systemDefault());
    }

    public CassandraQueryBuilder(CqlSession session, ZoneId zone) {
        this.session = session;
        this.zone = zone;
    }

    @Override
    public BoundQuery build(ValidatedQuery query) {
        ServiceDescriptor service = query.service();
        AstQuery ast = query.ast();
        if (!query.related().isEmpty()) {
            throw new ValidationException(ErrorCode.UNSUPPORTED, "Cassandra has no joins; filter on fields of '"
                    + service.name().value() + "' only, not on " + query.related().keySet(), Map.of());
        }
        Fields fields = Fields.of(service);
        Pushdown pushdown = pushdown(table(service.table().value()), ast.filter().orElse(null), fields);

        int limit = ast.take().map(Take::value).orElse(service.maxTake().value());
        int skip = ast.skip().map(value -> Math.max(0, value.value())).orElse(0);
        CqlExpressions expressions = new CqlExpressions(fields.columns(), fields.types(), zone);
        RowFilter filter = new RowFilter(ast.filter().orElse(null), fields.columns(), fields.caseSensitive(),
                expressions);
        Aggregate aggregate = ast.aggregate().orElse(null);
        if (aggregate == null) {
            return new CassandraQuery(pushdown.segments(), pushdown.parameters(), pushdown.bindValues(), filter,
                    RowOrder.of(query.effectiveSort(), fields.columns(), expressions), skip, limit, null,
                    top(query, fields.columns(), expressions));
        }
        return new CassandraQuery(pushdown.segments(), pushdown.parameters(), pushdown.bindValues(), filter, null, skip,
                limit, aggregate(query, aggregate, expressions), null);
    }

    private record Fields(Map<String, CqlIdentifier> columns, Set<String> caseSensitive, Map<String, FieldType> types) {

        static Fields of(ServiceDescriptor service) {
            Map<String, CqlIdentifier> columns = new HashMap<>();
            Set<String> caseSensitive = new HashSet<>();
            Map<String, FieldType> types = new HashMap<>();
            for (FieldMapping fm : service.fields()) {
                columns.put(fm.logical().value(), CqlIdentifier.fromInternal(fm.column().value()));
                types.put(fm.logical().value(), fm.type());
                if (fm.caseSensitive()) {
                    caseSensitive.add(fm.logical().value());
                }
            }
            return new Fields(columns, caseSensitive, types);
        }
    }

    private record Pushdown(List<String> segments, List<DslLiteral> parameters, List<Object> bindValues) {
    }

    private Pushdown pushdown(TableMetadata table, @Nullable FilterExpr filterExpr, Fields fields) {
        List<String> segments = new ArrayList<>();
        List<DslLiteral> parameters = new ArrayList<>();
        List<Object> bindValues = new ArrayList<>();
        StringBuilder cql = new StringBuilder("SELECT * FROM ").append(table.getKeyspace().asCql(true)).append('.')
                .append(table.getName().asCql(true));
        Map<CqlIdentifier, Restriction> restrictions = new HashMap<>();
        boolean where = false;
        List<FilterExpr> conjuncts = filterExpr == null ? List.of() : conjuncts(filterExpr, new ArrayList<>());
        for (FilterExpr conjunct : conjuncts) {
            if (!(conjunct instanceof Compare c)) {
                continue;
            }
            CqlIdentifier column = fields.columns().get(c.field().value());
            ColumnMetadata metadata = column == null ? null : table.getColumn(column).orElse(null);
            if (metadata == null || !pushable(table, metadata, c)) {
                continue;
            }
            Object bound = CqlValues.bindValue(metadata.getType(), c.value(),
                    fields.caseSensitive().contains(c.field().value()), zone);
            if (bound == null || !restrictions.computeIfAbsent(column, k -> new Restriction()).accepts(c.op())) {
                continue;
            }
            cql.append(where ? " AND " : " WHERE ").append(column.asCql(true)).append(' ').append(cqlOp(c.op()))
                    .append(' ');
            segments.add(cql.toString());
            cql.setLength(0);
            parameters.add(c.value());
            bindValues.add(bound);
            where = true;
        }
        if (where) {
            cql.append(" ALLOW FILTERING");
        }
        segments.add(cql.toString());
        return new Pushdown(segments, parameters, bindValues);
    }

    private static boolean pushable(TableMetadata table, ColumnMetadata metadata, Compare c) {
        boolean dayLiteral = c.value() instanceof TextLiteral t && CqlValues.dateOnly(t.value()).isPresent();
        return pushable(table, metadata, c.op()) && (!CqlValues.isText(metadata.getType()) || c.op() == ComparisonOp.EQ)
                && (!dayLiteral || c.op() == ComparisonOp.LT || c.op() == ComparisonOp.GE);
    }

    private static CassandraAggregate aggregate(ValidatedQuery query, Aggregate aggregate, CqlExpressions expressions) {
        List<String> rowColumns = new ArrayList<>(aggregate.groupNames());
        rowColumns.add(ValidatedQuery.valueField(aggregate).value());
        return new CassandraAggregate(aggregate.function(),
                aggregate.groupBy().stream().<Function<Row, @Nullable Object>>map(g -> row -> expressions.eval(g, row))
                        .toList(),
                aggregate.argument() == null ? null : row -> expressions.eval(aggregate.argument(), row),
                RowOrder.ofValues(query.effectiveSort(), rowColumns));
    }

    private static @Nullable UnaryOperator<List<Row>> top(ValidatedQuery query, Map<String, CqlIdentifier> columns,
            CqlExpressions expressions) {
        TopN top = query.ast().top().orElse(null);
        if (top == null) {
            return null;
        }
        Comparator<Row> order = RowOrder.of(query.topOrder(), columns, expressions);
        return rows -> {
            Map<List<Object>, List<Row>> partitions = new LinkedHashMap<>();
            for (Row row : rows) {
                List<Object> key = new ArrayList<>(top.partition().size());
                for (ValueExpr part : top.partition()) {
                    key.add(expressions.eval(part, row));
                }
                partitions.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
            }
            List<Row> kept = new ArrayList<>();
            for (List<Row> partition : partitions.values()) {
                if (order != null) {
                    partition.sort(order);
                }
                kept.addAll(partition.subList(0, Math.min(top.count(), partition.size())));
            }
            return kept;
        };
    }

    private TableMetadata table(String name) {
        int dot = name.indexOf('.');
        CqlIdentifier keyspace = dot >= 0 ? CqlIdentifier.fromInternal(name.substring(0, dot))
                : session.getKeyspace().orElseThrow(() -> new IllegalStateException("No keyspace for table '" + name
                        + "': set one on the CqlSession or use " + "@IntentService(table = \"keyspace.table\")"));
        CqlIdentifier table = CqlIdentifier.fromInternal(dot >= 0 ? name.substring(dot + 1) : name);
        return session.getMetadata().getKeyspace(keyspace).flatMap(ks -> ks.getTable(table))
                .or(() -> session.refreshSchema().getKeyspace(keyspace).flatMap(ks -> ks.getTable(table)))
                .orElseThrow(() -> new IllegalStateException(
                        "Cassandra table " + keyspace.asCql(true) + "." + table.asCql(true) + " not found"));
    }

    private static List<FilterExpr> conjuncts(FilterExpr expr, List<FilterExpr> out) {
        if (expr instanceof And a) {
            conjuncts(a.left(), out);
            conjuncts(a.right(), out);
        } else {
            out.add(expr);
        }
        return out;
    }

    private static boolean pushable(TableMetadata table, ColumnMetadata column, ComparisonOp op) {
        if (table.getPartitionKey().contains(column)) {
            return table.getPartitionKey().size() == 1 && op == ComparisonOp.EQ;
        }
        if (table.getClusteringColumns().containsKey(column) || column.isStatic()) {
            return false;
        }
        return op != ComparisonOp.NE && op != ComparisonOp.CONTAINS;
    }

    private static String cqlOp(ComparisonOp op) {
        return switch (op) {
        case EQ -> "=";
        case LT -> "<";
        case LE -> "<=";
        case GT -> ">";
        case GE -> ">=";
        case NE, CONTAINS -> throw new IllegalStateException(op + " is never pushed down");
        };
    }

    private static final class Restriction {
        private boolean equal;
        private boolean lower;
        private boolean upper;

        boolean accepts(ComparisonOp op) {
            if (equal) {
                return false;
            }
            switch (op) {
            case EQ -> {
                if (lower || upper) {
                    return false;
                }
                equal = true;
            }
            case GT, GE -> {
                if (lower) {
                    return false;
                }
                lower = true;
            }
            case LT, LE -> {
                if (upper) {
                    return false;
                }
                upper = true;
            }
            default -> {
                return false;
            }
            }
            return true;
        }
    }
}
