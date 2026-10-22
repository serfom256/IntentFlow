package io.github.serfom256.intentflow.core.pipeline.impl;

import io.github.serfom256.intentflow.core.ast.TextLiteral;
import io.github.serfom256.intentflow.core.catalog.FieldMapping;
import io.github.serfom256.intentflow.core.catalog.LogicalFieldName;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.ServiceRelation;
import io.github.serfom256.intentflow.core.schema.TableFacts;
import io.github.serfom256.intentflow.core.sql.plan.Select;
import io.github.serfom256.intentflow.core.sql.plan.SqlExpr;
import io.github.serfom256.intentflow.core.sql.plan.SqlPredicate;
import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SqlScope {

    static final String RELATED_ALIAS = "intentflow_r";
    private static final String JOIN_ALIAS = "intentflow_j";

    private final PlanContext context;
    private final ServiceDescriptor service;
    private final TableFacts facts;
    private final @Nullable String alias;
    private final boolean qualified;
    private final Map<String, ServiceDescriptor> related;
    private final Map<String, FieldMapping> fields = new HashMap<>();
    private final Map<String, Select.LeftJoin> joins = new LinkedHashMap<>();
    private final Map<String, SqlScope> joined = new HashMap<>();
    private @Nullable ExprTypes types;

    private SqlScope(PlanContext context, ServiceDescriptor service, @Nullable String alias, boolean qualified,
            Map<String, ServiceDescriptor> related) {
        this.context = context;
        this.service = service;
        this.facts = context.facts(service.table().value());
        this.alias = alias;
        this.qualified = qualified;
        this.related = related;
        for (FieldMapping fm : service.fields()) {
            fields.put(fm.logical().value(), fm);
        }
    }

    static SqlScope outer(PlanContext context, ServiceDescriptor service, Map<String, ServiceDescriptor> related,
            boolean qualified) {
        return new SqlScope(context, service, null, qualified, related);
    }

    SqlScope inner(String relationName) {
        return new SqlScope(context, related(relationName), RELATED_ALIAS, true, Map.of());
    }

    PlanContext context() {
        return context;
    }

    boolean isOuter() {
        return alias == null;
    }

    ExprTypes types() {
        if (types == null) {
            types = new ExprTypes(fields);
        }
        return types;
    }

    FieldMapping field(LogicalFieldName name) {
        FieldMapping fm = fields.get(name.value());
        if (fm == null) {
            throw new IllegalArgumentException("logical field '" + name.value() + "' has no column mapping");
        }
        return fm;
    }

    SqlExpr.Column column(LogicalFieldName name) {
        return column(field(name).column().value());
    }

    SqlExpr value(LogicalFieldName name) {
        List<String> names = ordinals(name);
        return names.isEmpty() ? column(name) : new SqlExpr.EnumName(column(name), names);
    }

    List<String> ordinals(LogicalFieldName name) {
        return facts.ordinalValues(field(name).column().value());
    }

    SqlExpr.Column column(String column) {
        return new SqlExpr.Column(alias != null ? alias : qualified ? reference() : null, column);
    }

    SqlExpr timestamp(LogicalFieldName name, String text) {
        return timestamp(name, Timestamps.local(text, context.zone()));
    }

    SqlExpr timestamp(LogicalFieldName name, LocalDateTime local) {
        if (zoned(name)) {
            OffsetDateTime at = local.atZone(context.zone()).toOffsetDateTime();
            return new SqlExpr.TimestampParam(new TextLiteral(at.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)), at);
        }
        return new SqlExpr.TimestampParam(new TextLiteral(local.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)), local);
    }

    boolean zoned(LogicalFieldName name) {
        return facts.isZoned(field(name).column().value());
    }

    boolean notNull(LogicalFieldName name) {
        return facts.isNotNull(field(name).column().value());
    }

    List<String> exactValues(LogicalFieldName name) {
        return facts.exactValues(field(name).column().value());
    }

    List<String> primaryKey() {
        return facts.primaryKey();
    }

    boolean unique(Collection<String> columns) {
        return facts.isUnique(columns);
    }

    String table() {
        return service.table().value();
    }

    String reference() {
        String table = table();
        return table.substring(table.lastIndexOf('.') + 1);
    }

    Select.Item allColumns() {
        return new Select.AllColumns(qualified ? reference() : null);
    }

    Select.From from() {
        return new Select.Table(table(), alias);
    }

    Select.From withJoins(Select.From from) {
        Select.From result = from;
        for (Select.LeftJoin join : joins.values()) {
            result = new Select.LeftJoin(result, join.right(), join.on());
        }
        return result;
    }

    List<SqlPredicate> restriction() {
        List<SqlPredicate> restriction = new ArrayList<>();
        String sql = context.restriction(service.name());
        if (sql != null) {
            restriction.add(new SqlPredicate.Raw(sql));
        }
        context.runtimeRestrictions(service).forEach(r -> restriction.add(new SqlPredicate.Bound(r.segments(),
                r.values())));
        return restriction;
    }

    private boolean restricted(ServiceDescriptor candidate) {
        return context.restriction(candidate.name()) != null || !context.runtimeRestrictions(candidate).isEmpty();
    }

    List<SqlPredicate> where(List<SqlPredicate> conditions) {
        List<SqlPredicate> where = new ArrayList<>(conditions);
        where.addAll(restriction());
        return where;
    }

    SqlPredicate correlation(String relationName) {
        ServiceRelation relation = relation(relationName);
        return new SqlPredicate.Compare(inner(relationName).column(relation.to()), "=",
                new SqlExpr.Column(reference(), field(relation.from()).column().value()));
    }

    boolean canJoin(String relationName) {
        ServiceRelation relation = relation(relationName);
        ServiceDescriptor target = related(relationName);
        TableFacts targetFacts = context.facts(target.table().value());
        String toColumn = target.fields().stream().filter(f -> f.logical().equals(relation.to())).findFirst()
                .map(f -> f.column().value()).orElse(relation.to().value());
        return isOuter() && !restricted(service) && !restricted(target) && targetFacts.isUnique(toColumn);
    }

    SqlScope join(String relationName) {
        SqlScope existing = joined.get(relationName);
        if (existing != null) {
            return existing;
        }
        ServiceRelation relation = relation(relationName);
        String joinAlias = JOIN_ALIAS + joins.size();
        SqlScope target = new SqlScope(context, related(relationName), joinAlias, true, Map.of());
        joins.put(relationName, new Select.LeftJoin(from(), new Select.Table(target.table(), joinAlias),
                List.of(new SqlPredicate.Compare(target.column(relation.to()), "=", column(relation.from())))));
        joined.put(relationName, target);
        return target;
    }

    private ServiceRelation relation(String name) {
        return service.relations().stream().filter(r -> r.name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("'" + name + "' is not a relation of "
                        + service.name().value()));
    }

    private ServiceDescriptor related(String name) {
        ServiceDescriptor target = related.get(name);
        if (target == null) {
            throw new IllegalArgumentException("relation '" + name + "' was not resolved by the validator");
        }
        return target;
    }
}
