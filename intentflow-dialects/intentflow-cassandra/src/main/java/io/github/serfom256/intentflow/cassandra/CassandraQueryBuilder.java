package io.github.serfom256.intentflow.cassandra;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.metadata.schema.ColumnMetadata;
import com.datastax.oss.driver.api.core.metadata.schema.TableMetadata;
import io.github.serfom256.intentflow.core.ast.Aggregate;
import io.github.serfom256.intentflow.core.ast.And;
import io.github.serfom256.intentflow.core.ast.AstQuery;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.Compare;
import io.github.serfom256.intentflow.core.ast.ComparisonOp;
import io.github.serfom256.intentflow.core.ast.DslLiteral;
import io.github.serfom256.intentflow.core.ast.FilterExpr;
import io.github.serfom256.intentflow.core.ast.TextLiteral;
import io.github.serfom256.intentflow.core.catalog.FieldMapping;
import io.github.serfom256.intentflow.core.catalog.LogicalFieldName;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.pipeline.SqlBuilder;
import io.github.serfom256.intentflow.core.pipeline.ValidatedQuery;
import io.github.serfom256.intentflow.core.pipeline.ValidationException;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
public final class CassandraQueryBuilder implements SqlBuilder {

    private final CqlSession session;

    @Override
    public BoundQuery build(ValidatedQuery query) {
        ServiceDescriptor service = query.service();
        AstQuery ast = query.ast();
        TableMetadata table = table(service.table().value());

        if (!query.related().isEmpty()) {
            throw new ValidationException("Cassandra has no joins; filter on fields of '" + service.name().value()
                    + "' only, not on " + query.related().keySet());
        }
        Map<String, CqlIdentifier> columns = new HashMap<>();
        Set<String> caseSensitive = new HashSet<>();
        for (FieldMapping fm : service.fields()) {
            columns.put(fm.logical().value(), CqlIdentifier.fromInternal(fm.column().value()));
            if (fm.caseSensitive()) {
                caseSensitive.add(fm.logical().value());
            }
        }

        List<String> segments = new ArrayList<>();
        List<DslLiteral> parameters = new ArrayList<>();
        List<Object> bindValues = new ArrayList<>();
        StringBuilder cql = new StringBuilder("SELECT * FROM ")
                .append(table.getKeyspace().asCql(true)).append('.').append(table.getName().asCql(true));

        Map<CqlIdentifier, Restriction> restrictions = new HashMap<>();
        boolean where = false;
        if (ast.filter() != null) {
            for (FilterExpr conjunct : conjuncts(ast.filter(), new ArrayList<>())) {
                if (!(conjunct instanceof Compare c)) {
                    continue;
                }
                CqlIdentifier column = columns.get(c.field().value());
                ColumnMetadata metadata = column == null ? null : table.getColumn(column).orElse(null);
                boolean dayLiteral = c.value() instanceof TextLiteral t
                        && CqlValues.dateOnly(t.value()).isPresent();
                if (metadata == null || !pushable(table, metadata, c.op())
                        || (CqlValues.isText(metadata.getType()) && c.op() != ComparisonOp.EQ)
                        || (dayLiteral && c.op() != ComparisonOp.LT && c.op() != ComparisonOp.GE)) {
                    continue;
                }
                Object bound = CqlValues.bindValue(metadata.getType(), c.value(),
                        caseSensitive.contains(c.field().value()));
                if (bound == null || !restrictions.computeIfAbsent(column, k -> new Restriction()).accepts(c.op())) {
                    continue;
                }
                cql.append(where ? " AND " : " WHERE ").append(column.asCql(true)).append(' ')
                        .append(cqlOp(c.op())).append(' ');
                segments.add(cql.toString());
                cql.setLength(0);
                parameters.add(c.value());
                bindValues.add(bound);
                where = true;
            }
        }
        if (where) {
            cql.append(" ALLOW FILTERING");
        }
        segments.add(cql.toString());

        int limit = ast.take() != null ? ast.take().value() : service.maxTake().value();
        int skip = ast.skip() != null ? Math.max(0, ast.skip().value()) : 0;
        RowFilter filter = new RowFilter(ast.filter(), columns, caseSensitive);
        Aggregate aggregate = ast.aggregate();
        if (aggregate == null) {
            return new CassandraQuery(segments, parameters, bindValues, filter,
                    RowOrder.of(query.effectiveSort(), columns), skip, limit);
        }
        List<String> rowColumns = new ArrayList<>(aggregate.groupBy().stream().map(LogicalFieldName::value).toList());
        rowColumns.add(ValidatedQuery.valueField(aggregate).value());
        CassandraAggregate spec = new CassandraAggregate(
                aggregate.function(),
                aggregate.groupBy().stream().map(f -> columns.get(f.value())).toList(),
                aggregate.field() == null ? null : columns.get(aggregate.field().value()),
                RowOrder.ofValues(query.effectiveSort(), rowColumns));
        return new CassandraQuery(segments, parameters, bindValues, filter, null, skip, limit, spec);
    }

    private TableMetadata table(String name) {
        int dot = name.indexOf('.');
        CqlIdentifier keyspace = dot >= 0
                ? CqlIdentifier.fromInternal(name.substring(0, dot))
                : session.getKeyspace().orElseThrow(() -> new IllegalStateException(
                        "No keyspace for table '" + name + "': set one on the CqlSession or use "
                                + "@IntentService(table = \"keyspace.table\")"));
        CqlIdentifier table = CqlIdentifier.fromInternal(dot >= 0 ? name.substring(dot + 1) : name);
        return session.getMetadata().getKeyspace(keyspace)
                .flatMap(ks -> ks.getTable(table))
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
