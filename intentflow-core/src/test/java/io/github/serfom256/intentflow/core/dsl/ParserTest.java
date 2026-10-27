package io.github.serfom256.intentflow.core.dsl;

import io.github.serfom256.intentflow.core.ast.And;
import io.github.serfom256.intentflow.core.ast.AstQuery;
import io.github.serfom256.intentflow.core.ast.BoolLiteral;
import io.github.serfom256.intentflow.core.ast.Compare;
import io.github.serfom256.intentflow.core.ast.ComparisonOp;
import io.github.serfom256.intentflow.core.ast.In;
import io.github.serfom256.intentflow.core.ast.IsNotNull;
import io.github.serfom256.intentflow.core.ast.IsNull;
import io.github.serfom256.intentflow.core.ast.Not;
import io.github.serfom256.intentflow.core.ast.NullLiteral;
import io.github.serfom256.intentflow.core.ast.NumberLiteral;
import io.github.serfom256.intentflow.core.ast.Or;
import io.github.serfom256.intentflow.core.ast.TextLiteral;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import io.github.serfom256.intentflow.core.dsl.factories.Lexers;
import io.github.serfom256.intentflow.core.dsl.factories.Parsers;
import io.github.serfom256.intentflow.core.pipeline.DslParseException;
import io.github.serfom256.intentflow.core.pipeline.UnsupportedRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParserTest {

    private final Lexer lexer = Lexers.create();
    private final Parser parser = Parsers.create();

    private AstQuery parse(String dsl) {
        return parser.parse(lexer.tokenize(DslSources.of(dsl)));
    }

    @Test
    void bareService() {
        AstQuery q = parse("books");
        assertThat(q.service().value()).isEqualTo("books");
        assertThat(q.filter()).isEmpty();
        assertThat(q.sort()).isEmpty();
        assertThat(q.skip()).isEmpty();
        assertThat(q.take()).isEmpty();
    }

    @Test
    void everyComparisonOperatorParses() {
        assertCompareOp("books | filter year == 2000", ComparisonOp.EQ);
        assertCompareOp("books | filter year != 2000", ComparisonOp.NE);
        assertCompareOp("books | filter year <  2000", ComparisonOp.LT);
        assertCompareOp("books | filter year <= 2000", ComparisonOp.LE);
        assertCompareOp("books | filter year >  2000", ComparisonOp.GT);
        assertCompareOp("books | filter year >= 2000", ComparisonOp.GE);
        assertCompareOp("books | filter title ~ \"ring\"", ComparisonOp.CONTAINS);
    }

    private void assertCompareOp(String dsl, ComparisonOp expected) {
        AstQuery q = parse(dsl);
        assertThat(q.filter().orElseThrow()).isInstanceOfSatisfying(Compare.class,
                c -> assertThat(c.op()).isEqualTo(expected));
    }

    @Test
    void stringNumberBoolTrueFalseNullValues() {
        assertThat(((Compare) parse("books | filter author == \"Doe\"").filter().orElseThrow()).value())
                .isInstanceOf(TextLiteral.class);
        assertThat(((Compare) parse("books | filter year == 42").filter().orElseThrow()).value())
                .isInstanceOfSatisfying(NumberLiteral.class,
                        n -> assertThat(n.value()).isEqualByComparingTo(new BigDecimal("42")));
        assertThat(((Compare) parse("books | filter inStock == true").filter().orElseThrow()).value())
                .isEqualTo(new BoolLiteral(true));
        assertThat(((Compare) parse("books | filter inStock == false").filter().orElseThrow()).value())
                .isEqualTo(new BoolLiteral(false));
    }

    @Test
    void eqNullProducesIsNull() {
        assertThat(parse("books | filter deletedAt == null").filter().orElseThrow()).isInstanceOf(IsNull.class);
    }

    @Test
    void neNullProducesIsNotNull() {
        assertThat(parse("books | filter deletedAt != null").filter().orElseThrow()).isInstanceOf(IsNotNull.class);
    }

    @Test
    void otherOpsWithNullAreRejected() {
        assertThatThrownBy(() -> parse("books | filter year < null")).isInstanceOf(DslParseException.class)
                .hasMessageContaining("'null' is only valid with '==' or '!='");
    }

    @Test
    void nullLiteralWorksInsideInList() {
        AstQuery q = parse("books | filter status in (\"a\", null)");
        In in = (In) q.filter().orElseThrow();
        assertThat(in.values()).hasSize(2);
        assertThat(in.values().get(1)).isInstanceOf(NullLiteral.class);
    }

    @Test
    void andHasHigherPrecedenceThanOr() {
        AstQuery q = parse("books | filter a == 1 or b == 2 and c == 3");
        Or or = (Or) q.filter().orElseThrow();
        assertThat(or.left()).isInstanceOf(Compare.class);
        assertThat(or.right()).isInstanceOfSatisfying(And.class, and -> {
            assertThat(and.left()).isInstanceOf(Compare.class);
            assertThat(and.right()).isInstanceOf(Compare.class);
        });
    }

    @Test
    void notBindsTighterThanAnd() {
        AstQuery q = parse("books | filter not a == 1 and b == 2");
        And and = (And) q.filter().orElseThrow();
        assertThat(and.left()).isInstanceOf(Not.class);
        assertThat(and.right()).isInstanceOf(Compare.class);
    }

    @Test
    void parensOverrideDefaultPrecedence() {
        AstQuery q = parse("books | filter (a == 1 or b == 2) and c == 3");
        And and = (And) q.filter().orElseThrow();
        assertThat(and.left()).isInstanceOf(Or.class);
        assertThat(and.right()).isInstanceOf(Compare.class);
    }

    @Test
    void doubleNot() {
        AstQuery q = parse("books | filter not not a == 1");
        Not outer = (Not) q.filter().orElseThrow();
        assertThat(outer.inner()).isInstanceOfSatisfying(Not.class,
                inner -> assertThat(inner.inner()).isInstanceOf(Compare.class));
    }

    @Test
    void inListWithMultipleValues() {
        AstQuery q = parse("books | filter status in (\"a\", \"b\", \"c\")");
        In in = (In) q.filter().orElseThrow();
        assertThat(in.values()).hasSize(3);
    }

    @Test
    void inListWithSingleValueIsAllowed() {
        AstQuery q = parse("books | filter status in (\"only\")");
        In in = (In) q.filter().orElseThrow();
        assertThat(in.values()).hasSize(1);
    }

    @Test
    void sortDefaultsToAsc() {
        AstQuery q = parse("books | sort year");
        assertThat(q.sort()).hasSize(1);
        assertThat(q.sort().get(0).direction()).isEqualTo(SortKey.Direction.ASC);
    }

    @Test
    void sortExplicitDesc() {
        AstQuery q = parse("books | sort year desc");
        assertThat(q.sort().get(0).direction()).isEqualTo(SortKey.Direction.DESC);
    }

    @Test
    void sortMultipleKeys() {
        AstQuery q = parse("books | sort year desc, title asc");
        assertThat(q.sort()).hasSize(2);
        assertThat(q.sort().get(0).direction()).isEqualTo(SortKey.Direction.DESC);
        assertThat(q.sort().get(1).direction()).isEqualTo(SortKey.Direction.ASC);
    }

    @Test
    void projectStageIsNoLongerPartOfTheLanguage() {
        assertThatThrownBy(() -> parse("books | project title")).isInstanceOf(DslParseException.class)
                .hasMessageContaining("expected stage keyword");
    }

    @Test
    void skipAndTake() {
        AstQuery q = parse("books | skip 40 | take 20");
        assertThat(q.skip().orElseThrow().value()).isEqualTo(40);
        assertThat(q.take().orElseThrow().value()).isEqualTo(20);
    }

    @Test
    void takeRejectsFractionalNumber() {
        assertThatThrownBy(() -> parse("books | take 1.5")).isInstanceOf(DslParseException.class)
                .hasMessageContaining("integer");
    }

    @Test
    void takeRejectsZero() {
        assertThatThrownBy(() -> parse("books | take 0")).isInstanceOf(DslParseException.class);
    }

    @Test
    void stagesInFreeOrder() {
        AstQuery q = parse("books | sort year | filter year >= 2000 | take 5");
        assertThat(q.filter().orElseThrow()).isInstanceOf(Compare.class);
        assertThat(q.sort()).hasSize(1);
        assertThat(q.take().orElseThrow().value()).isEqualTo(5);
    }

    @Test
    void duplicateFilterIsRejected() {
        assertThatThrownBy(() -> parse("books | filter a == 1 | filter b == 2")).isInstanceOf(DslParseException.class)
                .hasMessageContaining("duplicate 'filter'");
    }

    @Test
    void duplicateTakeIsRejected() {
        assertThatThrownBy(() -> parse("books | take 5 | take 10")).isInstanceOf(DslParseException.class)
                .hasMessageContaining("duplicate 'take'");
    }

    @Test
    void fullPipelineParses() {
        AstQuery q = parse(
                "books | filter author == \"Doe\" and year >= 2000" + " | sort year desc | skip 0 | take 20");

        assertThat(q.service().value()).isEqualTo("books");
        assertThat(q.filter().orElseThrow()).isInstanceOf(And.class);
        assertThat(q.sort()).hasSize(1);
        assertThat(q.skip().orElseThrow().value()).isZero();
        assertThat(q.take().orElseThrow().value()).isEqualTo(20);
    }

    @Test
    void emptyFilterIsRejected() {
        assertThatThrownBy(() -> parse("books | filter")).isInstanceOf(DslParseException.class);
    }

    @Test
    void unbalancedParensRejected() {
        assertThatThrownBy(() -> parse("books | filter (a == 1")).isInstanceOf(DslParseException.class)
                .hasMessageContaining("')'");
    }

    @Test
    void missingComparisonOperatorRejected() {
        assertThatThrownBy(() -> parse("books | filter year")).isInstanceOf(DslParseException.class)
                .hasMessageContaining("comparison operator");
    }

    @Test
    void unknownStageKeywordRejected() {
        assertThatThrownBy(() -> parse("books | asc")).isInstanceOf(DslParseException.class)
                .hasMessageContaining("unknown stage");
    }

    @Test
    void trailingTokensAfterPipelineRejected() {
        assertThatThrownBy(() -> parse("books extra")).isInstanceOf(DslParseException.class)
                .hasMessageContaining("unexpected");
    }

    @Test
    void missingServiceNameRejected() {
        assertThatThrownBy(() -> parse("| filter a == 1")).isInstanceOf(DslParseException.class)
                .hasMessageContaining("service name");
    }

    @Test
    void errorMessageIncludesLineAndColumn() {
        assertThatThrownBy(() -> parse("books\n| filter a")).isInstanceOf(DslParseException.class)
                .hasMessageContaining("line 2");
    }

    @Test
    void unsupportedWithReasonThrowsUnsupportedRequest() {
        assertThatThrownBy(() -> parse("unsupported \"no publisher field\"")).isInstanceOfSatisfying(
                UnsupportedRequestException.class, e -> assertThat(e.reason()).isEqualTo("no publisher field"));
    }

    @Test
    void unsupportedWithoutReasonIsAccepted() {
        assertThatThrownBy(() -> parse("unsupported")).isInstanceOfSatisfying(UnsupportedRequestException.class,
                e -> assertThat(e.reason()).isEqualTo("no reason given"));
    }

    @Test
    void unsupportedMustBeTheWholeQuery() {
        assertThatThrownBy(() -> parse("unsupported \"x\" | take 1")).isInstanceOf(DslParseException.class);
        assertThatThrownBy(() -> parse("books | filter unsupported == 1")).isInstanceOf(DslParseException.class);
    }
}
