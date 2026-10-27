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
import io.github.serfom256.intentflow.core.catalog.LogicalFieldName;
import io.github.serfom256.intentflow.core.catalog.factories.LogicalFieldNames;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

final class RelationPaths {

    private RelationPaths() {
    }

    static @Nullable String prefix(LogicalFieldName field) {
        int dot = field.value().indexOf('.');
        return dot < 0 ? null : field.value().substring(0, dot);
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
