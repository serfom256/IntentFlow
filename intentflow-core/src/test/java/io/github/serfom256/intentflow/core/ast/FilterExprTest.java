package io.github.serfom256.intentflow.core.ast;

import io.github.serfom256.intentflow.core.catalog.LogicalFieldName;
import io.github.serfom256.intentflow.core.catalog.factories.LogicalFieldNames;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FilterExprTest {

    private static final LogicalFieldName AUTHOR = LogicalFieldNames.of("author");
    private static final LogicalFieldName YEAR = LogicalFieldNames.of("year");
    private static final LogicalFieldName DELETED_AT = LogicalFieldNames.of("deletedAt");

    @Test
    void sealedHierarchyIsExhaustiveInSwitch() {
        Compare leaf = new Compare(AUTHOR, ComparisonOp.EQ, new TextLiteral("Doe"));

        FilterExpr[] variants = { new And(leaf, leaf), new Or(leaf, leaf), new Not(leaf), leaf,
                new In(YEAR, List.of(new NumberLiteral(new BigDecimal("2020")))), new IsNull(DELETED_AT),
                new IsNotNull(DELETED_AT), new ExprCompare(new FieldRef(YEAR), ComparisonOp.GT, new FieldRef(YEAR)),
                new ExprIn(new FieldRef(YEAR), List.of(new NumberLiteral(BigDecimal.ONE))), };

        List<String> labels = new ArrayList<>();
        for (FilterExpr expr : variants) {
            String label = switch (expr) {
            case And a -> "and";
            case Or o -> "or";
            case Not n -> "not";
            case Compare c -> "compare";
            case In i -> "in";
            case IsNull n -> "isNull";
            case IsNotNull n -> "isNotNull";
            case ExprCompare c -> "exprCompare";
            case ExprIn i -> "exprIn";
            };
            labels.add(label);
        }

        assertThat(labels).containsExactly("and", "or", "not", "compare", "in", "isNull", "isNotNull", "exprCompare",
                "exprIn");
    }

    @Test
    void compareCarriesFieldOpAndValue() {
        Compare c = new Compare(YEAR, ComparisonOp.GE, new NumberLiteral(new BigDecimal("2000")));
        assertThat(c.field()).isEqualTo(YEAR);
        assertThat(c.op()).isEqualTo(ComparisonOp.GE);
        assertThat(c.value()).isInstanceOf(NumberLiteral.class);
    }

    @Test
    void inRejectsEmptyValues() {
        assertThatThrownBy(() -> new In(YEAR, List.of())).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-empty");
    }

    @Test
    void inDefensivelyCopiesValues() {
        List<DslLiteral> mutable = new ArrayList<>();
        mutable.add(new NumberLiteral(BigDecimal.ONE));
        In in = new In(YEAR, mutable);
        mutable.clear();
        assertThat(in.values()).hasSize(1);
    }

    @Test
    void andHoldsBothBranches() {
        Compare l = new Compare(YEAR, ComparisonOp.GE, new NumberLiteral(BigDecimal.ONE));
        Compare r = new Compare(AUTHOR, ComparisonOp.EQ, new TextLiteral("X"));
        And and = new And(l, r);
        assertThat(and.left()).isSameAs(l);
        assertThat(and.right()).isSameAs(r);
    }

    @Test
    void notWrapsInner() {
        IsNull inner = new IsNull(DELETED_AT);
        Not not = new Not(inner);
        assertThat(not.inner()).isSameAs(inner);
    }
}
