package io.github.serfom256.intentflow.core.pipeline.impl;

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
import io.github.serfom256.intentflow.core.catalog.FieldMapping;
import io.github.serfom256.intentflow.core.catalog.FieldType;
import io.github.serfom256.intentflow.core.catalog.LogicalFieldName;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.ServiceRelation;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import io.github.serfom256.intentflow.core.pipeline.AstValidator;
import io.github.serfom256.intentflow.core.pipeline.ValidatedQuery;
import io.github.serfom256.intentflow.core.pipeline.ValidationException;
import io.github.serfom256.intentflow.core.pipeline.factories.ValidatedQueries;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DefaultAstValidator implements AstValidator {

    @Override
    public ValidatedQuery validate(AstQuery ast, ServiceCatalog catalog) {
        ServiceDescriptor service = catalog.get(ast.service());
        Fields fields = new Fields(service, catalog);

        if (ast.filter() != null) {
            validateFilter(ast.filter(), fields);
        }
        Aggregate aggregate = ast.aggregate();
        if (aggregate == null) {
            for (SortKey sk : ast.sort()) {
                fields.own(sk.field(), "sort");
            }
        } else {
            validateAggregate(aggregate, ast.sort(), fields);
        }

        if (ast.take() != null && ast.take().value() > service.maxTake().value()) {
            throw new ValidationException(
                    "take value " + ast.take().value()
                            + " exceeds maxTake " + service.maxTake().value()
                            + " for service '" + service.name().value() + "'");
        }

        return ValidatedQueries.of(service, ast, fields.related);
    }

    private static void validateAggregate(Aggregate aggregate, List<SortKey> sort, Fields fields) {
        for (LogicalFieldName group : aggregate.groupBy()) {
            fields.own(group, "group");
        }
        if (aggregate.field() != null) {
            FieldMapping fm = fields.own(aggregate.field(), aggregate.function().keyword());
            boolean numeric = fm.type() == FieldType.NUMBER || fm.type() == FieldType.ANY;
            if ((aggregate.function() == AggregateFunction.SUM || aggregate.function() == AggregateFunction.AVG)
                    && !numeric) {
                throw mismatch(fm, aggregate.function().keyword() + " needs a number field");
            }
        }
        String value = ValidatedQuery.valueField(aggregate).value();
        for (SortKey sk : sort) {
            boolean grouped = aggregate.groupBy().stream().anyMatch(g -> g.value().equals(sk.field().value()));
            if (!grouped && !sk.field().value().equals(value)) {
                throw new ValidationException("aggregate queries sort by '" + value + "' or a group field, not '"
                        + sk.field().value() + "'");
            }
        }
    }

    private static final class Fields {

        private final ServiceDescriptor service;
        private final ServiceCatalog catalog;
        private final Map<String, FieldMapping> queryable;
        private final Map<String, ServiceDescriptor> related = new HashMap<>();

        Fields(ServiceDescriptor service, ServiceCatalog catalog) {
            this.service = service;
            this.catalog = catalog;
            this.queryable = queryableFields(service);
        }

        FieldMapping own(LogicalFieldName name, String stage) {
            if (name.value().contains(".")) {
                throw new ValidationException("'" + stage + "' only works on fields of '"
                        + service.name().value() + "', not on related field '" + name.value() + "'");
            }
            return lookup(queryable, name.value(), name.value(), stage);
        }

        FieldMapping any(LogicalFieldName name, String stage) {
            int dot = name.value().indexOf('.');
            if (dot < 0) {
                return own(name, stage);
            }
            String relationName = name.value().substring(0, dot);
            ServiceRelation relation = service.relations().stream()
                    .filter(r -> r.name().equals(relationName))
                    .findFirst()
                    .orElseThrow(() -> new ValidationException("'" + relationName + "' is not a relation of '"
                            + service.name().value() + "' (referenced in '" + stage + "')"));
            ServiceDescriptor target = catalog.find(relation.target()).orElseThrow(() -> new ValidationException(
                    "relation '" + relationName + "' targets unknown service '" + relation.target().value() + "'"));
            if (target.fields().stream().noneMatch(f -> f.logical().value().equals(relation.to().value()))) {
                throw new ValidationException("relation '" + relationName + "' links to undeclared field '"
                        + relation.to().value() + "' of '" + target.name().value() + "'");
            }
            related.put(relationName, target);
            return lookup(queryableFields(target), name.value().substring(dot + 1), name.value(), stage);
        }

        private static FieldMapping lookup(Map<String, FieldMapping> fields, String key, String shown, String stage) {
            FieldMapping fm = fields.get(key);
            if (fm == null) {
                throw new ValidationException(
                        "field '" + shown + "' is not declared or not queryable (referenced in '" + stage + "')");
            }
            return fm;
        }
    }

    private static Map<String, FieldMapping> queryableFields(ServiceDescriptor service) {
        Map<String, FieldMapping> fields = new HashMap<>();
        for (FieldMapping fm : service.fields()) {
            if (fm.queryable()) {
                fields.put(fm.logical().value(), fm);
            }
        }
        return fields;
    }

    private void validateFilter(FilterExpr expr, Fields fields) {
        switch (expr) {
            case And a -> {
                validateFilter(a.left(), fields);
                validateFilter(a.right(), fields);
            }
            case Or o -> {
                validateFilter(o.left(), fields);
                validateFilter(o.right(), fields);
            }
            case Not n -> validateFilter(n.inner(), fields);
            case Compare c -> checkCompare(c, fields.any(c.field(), "filter"));
            case In i -> {
                FieldMapping fm = fields.any(i.field(), "filter");
                for (DslLiteral v : i.values()) {
                    checkValue(fm, ComparisonOp.EQ, v);
                }
            }
            case IsNull i -> fields.any(i.field(), "filter");
            case IsNotNull i -> fields.any(i.field(), "filter");
        }
    }

    private static void checkCompare(Compare c, FieldMapping fm) {
        if (c.op() == ComparisonOp.CONTAINS && !(c.value() instanceof TextLiteral)) {
            throw mismatch(fm, "'~' needs a text value");
        }
        if (c.op() == ComparisonOp.CONTAINS && fm.type() != FieldType.TEXT && fm.type() != FieldType.ANY) {
            throw mismatch(fm, "'~' only works on text fields");
        }
        checkValue(fm, c.op(), c.value());
    }

    private static void checkValue(FieldMapping fm, ComparisonOp op, DslLiteral value) {
        if (value instanceof NullLiteral) {
            throw mismatch(fm, "use '== null' / '!= null' to test for missing values");
        }
        switch (fm.type()) {
            case ANY -> { }
            case TEXT -> {
                if (!(value instanceof TextLiteral t)) {
                    throw mismatch(fm, "expected a \"text\" value, got " + describe(value));
                }
                if (op != ComparisonOp.CONTAINS && !fm.allowedValues().isEmpty()
                        && fm.allowedValues().stream().noneMatch(v -> v.equalsIgnoreCase(t.value()))) {
                    throw mismatch(fm, "'" + t.value() + "' is not one of " + fm.allowedValues());
                }
            }
            case NUMBER -> {
                if (!(value instanceof NumberLiteral)) {
                    throw mismatch(fm, "expected a number, got " + describe(value));
                }
            }
            case BOOLEAN -> {
                if (!(value instanceof BoolLiteral)) {
                    throw mismatch(fm, "expected true or false, got " + describe(value));
                }
                if (op != ComparisonOp.EQ && op != ComparisonOp.NE) {
                    throw mismatch(fm, "booleans only support '==' and '!='");
                }
            }
            case TIMESTAMP -> {
                if (!(value instanceof TextLiteral t) || !Timestamps.isValid(t.value())) {
                    throw mismatch(fm, "expected a date like \"2026-01-31\" or \"2026-01-31T10:15:00\", got "
                            + describe(value));
                }
            }
        }
    }

    private static String describe(DslLiteral value) {
        return switch (value) {
            case TextLiteral t -> "\"" + t.value() + "\"";
            case NumberLiteral n -> n.value().toPlainString();
            case BoolLiteral b -> Boolean.toString(b.value());
            case NullLiteral n -> "null";
        };
    }

    private static ValidationException mismatch(FieldMapping fm, String detail) {
        return new ValidationException("field '" + fm.logical().value() + "' ("
                + fm.type().name().toLowerCase(Locale.ROOT) + "): " + detail);
    }
}
