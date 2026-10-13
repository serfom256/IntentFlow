package io.github.serfom256.intentflow.core.pipeline.impl;

import io.github.serfom256.intentflow.core.ast.Aggregate;
import io.github.serfom256.intentflow.core.ast.And;
import io.github.serfom256.intentflow.core.ast.AstQuery;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.Compare;
import io.github.serfom256.intentflow.core.ast.ComparisonOp;
import io.github.serfom256.intentflow.core.ast.DslLiteral;
import io.github.serfom256.intentflow.core.ast.FilterExpr;
import io.github.serfom256.intentflow.core.ast.In;
import io.github.serfom256.intentflow.core.ast.IsNotNull;
import io.github.serfom256.intentflow.core.ast.IsNull;
import io.github.serfom256.intentflow.core.ast.Not;
import io.github.serfom256.intentflow.core.ast.Or;
import io.github.serfom256.intentflow.core.ast.TextLiteral;
import io.github.serfom256.intentflow.core.ast.factories.BoundQueries;
import io.github.serfom256.intentflow.core.catalog.FieldMapping;
import io.github.serfom256.intentflow.core.catalog.FieldType;
import io.github.serfom256.intentflow.core.catalog.LogicalFieldName;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.ServiceRelation;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import io.github.serfom256.intentflow.core.catalog.factories.LogicalFieldNames;
import io.github.serfom256.intentflow.core.pipeline.SqlBuilder;
import io.github.serfom256.intentflow.core.pipeline.SqlDialect;
import io.github.serfom256.intentflow.core.pipeline.SqlWriter;
import io.github.serfom256.intentflow.core.pipeline.ValidatedQuery;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public final class DefaultSqlBuilder implements SqlBuilder {

    private static final String RELATED_ALIAS = "intentflow_r";

    private final SqlDialect dialect;

    @Override
    public BoundQuery build(ValidatedQuery query) {
        ServiceDescriptor service = query.service();
        AstQuery ast = query.ast();
        Sql sql = new Sql();
        Scope outer = new Scope(service, null, query.related());
        Aggregate aggregate = ast.aggregate();

        sql.text("SELECT ");
        if (aggregate == null) {
            sql.text("*");
        } else {
            for (LogicalFieldName group : aggregate.groupBy()) {
                sql.text(outer.column(group)).text(", ");
            }
            sql.text(aggregateExpression(aggregate, outer));
        }
        sql.text(" FROM ").text(dialect.quote(service.table().value()));

        if (ast.filter() != null) {
            sql.text(" WHERE ");
            appendFilter(ast.filter(), sql, outer);
        }

        if (aggregate != null && !aggregate.groupBy().isEmpty()) {
            sql.text(" GROUP BY ");
            for (int i = 0; i < aggregate.groupBy().size(); i++) {
                sql.text(i > 0 ? ", " : "").text(outer.column(aggregate.groupBy().get(i)));
            }
        }

        List<SortKey> sort = query.effectiveSort();
        if (!sort.isEmpty()) {
            sql.text(" ORDER BY ");
            String value = aggregate == null ? null : ValidatedQuery.valueField(aggregate).value();
            for (int i = 0; i < sort.size(); i++) {
                SortKey key = sort.get(i);
                if (i > 0) sql.text(", ");
                boolean byValue = aggregate != null && key.field().value().equals(value)
                        && aggregate.groupBy().stream().noneMatch(g -> g.value().equals(value));
                String expression = byValue ? aggregateExpression(aggregate, outer) : outer.column(key.field());
                dialect.orderBy(sql, expression, key.direction());
            }
        }

        int limit = ast.take() != null ? ast.take().value() : service.maxTake().value();
        dialect.limit(sql, limit, ast.skip() != null ? Math.max(0, ast.skip().value()) : 0);

        return sql.toBoundQuery();
    }

    private static String aggregateExpression(Aggregate aggregate, Scope scope) {
        return switch (aggregate.function()) {
            case COUNT -> "COUNT(*)";
            case SUM -> "SUM(" + scope.column(field(aggregate)) + ")";
            case AVG -> "AVG(" + scope.column(field(aggregate)) + ")";
            case MIN -> "MIN(" + scope.column(field(aggregate)) + ")";
            case MAX -> "MAX(" + scope.column(field(aggregate)) + ")";
        };
    }

    private static LogicalFieldName field(Aggregate aggregate) {
        if (aggregate.field() == null) {
            throw new IllegalStateException(aggregate.function().keyword() + " needs a field");
        }
        return aggregate.field();
    }

    private void appendFilter(FilterExpr expr, Sql sql, Scope scope) {
        if (scope.isOuter() && expr instanceof And) {
            appendConjunction(conjuncts(expr, new ArrayList<>()), sql, scope);
            return;
        }
        String relation = scope.isOuter() ? relationOf(expr) : null;
        if (relation != null) {
            appendExists(relation, List.of(expr), sql, scope);
            return;
        }
        switch (expr) {
            case And a -> {
                sql.text("(");
                appendFilter(a.left(), sql, scope);
                sql.text(" AND ");
                appendFilter(a.right(), sql, scope);
                sql.text(")");
            }
            case Or o -> {
                sql.text("(");
                appendFilter(o.left(), sql, scope);
                sql.text(" OR ");
                appendFilter(o.right(), sql, scope);
                sql.text(")");
            }
            case Not n -> {
                sql.text("NOT (");
                appendFilter(n.inner(), sql, scope);
                sql.text(")");
            }
            case Compare c -> appendCompare(c, sql, scope);
            case In i -> appendIn(i, sql, scope);
            case IsNull i -> sql.text(scope.column(i.field())).text(" IS NULL");
            case IsNotNull i -> sql.text(scope.column(i.field())).text(" IS NOT NULL");
        }
    }

    private void appendConjunction(List<FilterExpr> parts, Sql sql, Scope scope) {
        Map<String, List<FilterExpr>> byRelation = new LinkedHashMap<>();
        List<Object> order = new ArrayList<>();
        for (FilterExpr part : parts) {
            String relation = relationOf(part);
            if (relation == null) {
                order.add(part);
            } else {
                byRelation.computeIfAbsent(relation, r -> {
                    order.add(r);
                    return new ArrayList<>();
                }).add(part);
            }
        }
        if (order.size() > 1) sql.text("(");
        for (int i = 0; i < order.size(); i++) {
            if (i > 0) sql.text(" AND ");
            Object item = order.get(i);
            if (item instanceof String relation) {
                appendExists(relation, byRelation.get(relation), sql, scope);
            } else {
                appendFilter((FilterExpr) item, sql, scope);
            }
        }
        if (order.size() > 1) sql.text(")");
    }

    private void appendExists(String relationName, List<FilterExpr> parts, Sql sql, Scope outer) {
        ServiceRelation relation = outer.relation(relationName);
        ServiceDescriptor target = outer.related(relationName);
        Scope inner = new Scope(target, RELATED_ALIAS, Map.of());
        sql.text("EXISTS (SELECT 1 FROM ").text(dialect.quote(target.table().value())).text(" ")
                .text(dialect.quote(RELATED_ALIAS)).text(" WHERE ")
                .text(inner.column(relation.to())).text(" = ")
                .text(dialect.quote(outer.service.table().value())).text(".").text(outer.column(relation.from()));
        for (FilterExpr part : parts) {
            sql.text(" AND ");
            appendFilter(stripRelation(part, relationName), sql, inner);
        }
        sql.text(")");
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

    private static @Nullable String relationOf(FilterExpr expr) {
        if (expr instanceof Not) {
            return null;
        }
        List<String> prefixes = new ArrayList<>();
        collectPrefixes(expr, prefixes);
        String first = prefixes.getFirst();
        return first != null && prefixes.stream().allMatch(first::equals) ? first : null;
    }

    private static void collectPrefixes(FilterExpr expr, List<String> out) {
        switch (expr) {
            case And a -> {
                collectPrefixes(a.left(), out);
                collectPrefixes(a.right(), out);
            }
            case Or o -> {
                collectPrefixes(o.left(), out);
                collectPrefixes(o.right(), out);
            }
            case Not n -> collectPrefixes(n.inner(), out);
            case Compare c -> out.add(prefix(c.field()));
            case In i -> out.add(prefix(i.field()));
            case IsNull i -> out.add(prefix(i.field()));
            case IsNotNull i -> out.add(prefix(i.field()));
        }
    }

    private static @Nullable String prefix(LogicalFieldName field) {
        int dot = field.value().indexOf('.');
        return dot < 0 ? null : field.value().substring(0, dot);
    }

    private static FilterExpr stripRelation(FilterExpr expr, String relation) {
        return switch (expr) {
            case And a -> new And(stripRelation(a.left(), relation), stripRelation(a.right(), relation));
            case Or o -> new Or(stripRelation(o.left(), relation), stripRelation(o.right(), relation));
            case Not n -> new Not(stripRelation(n.inner(), relation));
            case Compare c -> new Compare(strip(c.field(), relation), c.op(), c.value());
            case In i -> new In(strip(i.field(), relation), i.values());
            case IsNull i -> new IsNull(strip(i.field(), relation));
            case IsNotNull i -> new IsNotNull(strip(i.field(), relation));
        };
    }

    private static LogicalFieldName strip(LogicalFieldName field, String relation) {
        return LogicalFieldNames.of(field.value().substring(relation.length() + 1));
    }

    private void appendCompare(Compare c, Sql sql, Scope scope) {
        String col = scope.column(c.field());
        FieldMapping fm = scope.field(c.field());
        if (c.op() == ComparisonOp.CONTAINS) {
            if (!(c.value() instanceof TextLiteral t)) {
                throw new IllegalArgumentException(
                        "'~' (CONTAINS) requires a text value; got " + c.value().getClass().getSimpleName());
            }
            dialect.contains(sql, col, t.value());
            return;
        }
        if (fm.type() == FieldType.TIMESTAMP && c.value() instanceof TextLiteral) {
            sql.text(col).text(" " + sqlOp(c.op()) + " ");
            dialect.timestamp(sql, c.value());
        } else if (caseInsensitive(fm, c.value()) && (c.op() == ComparisonOp.EQ || c.op() == ComparisonOp.NE)) {
            sql.text("LOWER(").text(col).text(") " + sqlOp(c.op()) + " LOWER(").param(c.value()).text(")");
        } else {
            sql.text(col).text(" " + sqlOp(c.op()) + " ").param(c.value());
        }
    }

    private void appendIn(In in, Sql sql, Scope scope) {
        List<DslLiteral> values = in.values();
        FieldMapping fm = scope.field(in.field());
        boolean lower = values.stream().allMatch(v -> caseInsensitive(fm, v));
        boolean timestamp = fm.type() == FieldType.TIMESTAMP;
        String col = scope.column(in.field());
        sql.text(lower ? "LOWER(" + col + ")" : col).text(" IN (");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sql.text(", ");
            if (lower) {
                sql.text("LOWER(").param(values.get(i)).text(")");
            } else if (timestamp) {
                dialect.timestamp(sql, values.get(i));
            } else {
                sql.param(values.get(i));
            }
        }
        sql.text(")");
    }

    private static boolean caseInsensitive(FieldMapping fm, DslLiteral value) {
        return value instanceof TextLiteral && !fm.caseSensitive()
                && (fm.type() == FieldType.TEXT || fm.type() == FieldType.ANY);
    }

    private static String sqlOp(ComparisonOp op) {
        return switch (op) {
            case EQ -> "=";
            case NE -> "<>";
            case LT -> "<";
            case LE -> "<=";
            case GT -> ">";
            case GE -> ">=";
            case CONTAINS -> throw new IllegalStateException("CONTAINS handled separately");
        };
    }

    private final class Scope {

        private final ServiceDescriptor service;
        private final @Nullable String alias;
        private final Map<String, ServiceDescriptor> related;
        private final Map<String, FieldMapping> fields = new HashMap<>();

        Scope(ServiceDescriptor service, @Nullable String alias, Map<String, ServiceDescriptor> related) {
            this.service = service;
            this.alias = alias;
            this.related = related;
            for (FieldMapping fm : service.fields()) {
                fields.put(fm.logical().value(), fm);
            }
        }

        boolean isOuter() {
            return alias == null;
        }

        FieldMapping field(LogicalFieldName name) {
            FieldMapping fm = fields.get(name.value());
            if (fm == null) {
                throw new IllegalArgumentException("logical field '" + name.value() + "' has no column mapping");
            }
            return fm;
        }

        String column(LogicalFieldName name) {
            String column = dialect.quote(field(name).column().value());
            return alias == null ? column : dialect.quote(alias) + "." + column;
        }

        ServiceRelation relation(String name) {
            return service.relations().stream().filter(r -> r.name().equals(name)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("'" + name + "' is not a relation of "
                            + service.name().value()));
        }

        ServiceDescriptor related(String name) {
            ServiceDescriptor target = related.get(name);
            if (target == null) {
                throw new IllegalArgumentException("relation '" + name + "' was not resolved by the validator");
            }
            return target;
        }
    }

    private static final class Sql implements SqlWriter {

        private final List<String> segments = new ArrayList<>();
        private final List<DslLiteral> params = new ArrayList<>();
        private final StringBuilder current = new StringBuilder(128);

        @Override
        public Sql text(String text) {
            current.append(text);
            return this;
        }

        @Override
        public Sql param(DslLiteral value) {
            segments.add(current.toString());
            current.setLength(0);
            params.add(value);
            return this;
        }

        BoundQuery toBoundQuery() {
            List<String> all = new ArrayList<>(segments);
            all.add(current.toString());
            return BoundQueries.of(all, params);
        }
    }
}
