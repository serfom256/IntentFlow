package io.github.serfom256.intentflow.core.pipeline.impl;

import io.github.serfom256.intentflow.core.ast.And;
import io.github.serfom256.intentflow.core.ast.Compare;
import io.github.serfom256.intentflow.core.ast.ExprCompare;
import io.github.serfom256.intentflow.core.ast.ExprIn;
import io.github.serfom256.intentflow.core.ast.FilterExpr;
import io.github.serfom256.intentflow.core.ast.In;
import io.github.serfom256.intentflow.core.ast.IsNotNull;
import io.github.serfom256.intentflow.core.ast.IsNull;
import io.github.serfom256.intentflow.core.ast.Not;
import io.github.serfom256.intentflow.core.ast.Or;
import io.github.serfom256.intentflow.core.catalog.FieldMapping;
import io.github.serfom256.intentflow.core.catalog.FieldType;
import io.github.serfom256.intentflow.core.catalog.LogicalFieldName;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import io.github.serfom256.intentflow.core.catalog.factories.LogicalFieldNames;
import io.github.serfom256.intentflow.core.catalog.factories.SortKeys;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class RelationPaths {

    private RelationPaths() {
    }

    static final Set<String> MODIFIERS = Set.of("latest", "earliest");

    static @Nullable String prefix(LogicalFieldName field) {
        String[] parts = field.value().split("\\.", 3);
        if (parts.length < 2) {
            return null;
        }
        return parts.length == 3 && MODIFIERS.contains(parts[1]) ? parts[0] + "." + parts[1] : parts[0];
    }

    static String base(String relation) {
        int dot = relation.indexOf('.');
        return dot < 0 ? relation : relation.substring(0, dot);
    }

    static @Nullable String modifier(String relation) {
        int dot = relation.indexOf('.');
        return dot < 0 ? null : relation.substring(dot + 1);
    }

    static Optional<List<SortKey>> recency(ServiceDescriptor target, String modifier) {
        Map<String, FieldMapping> fields = new HashMap<>();
        target.fields().forEach(f -> fields.put(f.logical().value(), f));
        List<LogicalFieldName> dates = target.fields().stream()
                .filter(f -> f.queryable() && f.type() == FieldType.TIMESTAMP).map(FieldMapping::logical).toList();
        Optional<LogicalFieldName> date = target.defaultSort().stream().map(SortKey::field)
                .filter(f -> fields.containsKey(f.value()) && fields.get(f.value()).type() == FieldType.TIMESTAMP)
                .findFirst().or(() -> dates.size() == 1 ? Optional.of(dates.getFirst()) : Optional.empty());
        return date.map(d -> {
            boolean latest = modifier.equals("latest");
            List<SortKey> keys = new ArrayList<>();
            keys.add(SortKeys.of(d, latest ? SortKey.Direction.DESC : SortKey.Direction.ASC));
            for (SortKey key : target.defaultSort()) {
                if (!key.field().equals(d)) {
                    SortKey.Direction direction = latest ? key.direction()
                            : key.direction() == SortKey.Direction.DESC ? SortKey.Direction.ASC
                                    : SortKey.Direction.DESC;
                    keys.add(SortKeys.of(key.field(), direction));
                }
            }
            return List.copyOf(keys);
        });
    }

    static LogicalFieldName strip(LogicalFieldName field, String relation) {
        return LogicalFieldNames.of(field.value().substring(relation.length() + 1));
    }

    static List<FilterExpr> conjuncts(FilterExpr expr) {
        List<FilterExpr> out = new ArrayList<>();
        collectConjuncts(expr, out);
        return out;
    }

    static @Nullable String relationOf(FilterExpr expr) {
        if (expr instanceof Not) {
            return null;
        }
        List<String> prefixes = new ArrayList<>();
        collectPrefixes(expr, prefixes);
        String first = prefixes.getFirst();
        return first != null && prefixes.stream().allMatch(first::equals) ? first : null;
    }

    static FilterExpr stripRelation(FilterExpr expr, String relation) {
        return switch (expr) {
        case And a -> new And(stripRelation(a.left(), relation), stripRelation(a.right(), relation));
        case Or o -> new Or(stripRelation(o.left(), relation), stripRelation(o.right(), relation));
        case Not n -> new Not(stripRelation(n.inner(), relation));
        case Compare c -> new Compare(strip(c.field(), relation), c.op(), c.value());
        case In i -> new In(strip(i.field(), relation), i.values());
        case IsNull i -> new IsNull(strip(i.field(), relation));
        case IsNotNull i -> new IsNotNull(strip(i.field(), relation));
        case ExprCompare c -> c;
        case ExprIn i -> i;
        };
    }

    private static void collectConjuncts(FilterExpr expr, List<FilterExpr> out) {
        if (expr instanceof And a) {
            collectConjuncts(a.left(), out);
            collectConjuncts(a.right(), out);
        } else {
            out.add(expr);
        }
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
        case ExprCompare c -> out.add(null);
        case ExprIn i -> out.add(null);
        }
    }
}
