package io.github.serfom256.intentflow.core.ast;

import io.github.serfom256.intentflow.core.ast.factories.BoundQueries;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoundQueryTest {

    private static final BoundQuery QUERY = BoundQueries.of(
            List.of("select * from book where year >= ", " and title = ", " and flag = ", ""),
            List.of(new NumberLiteral(new BigDecimal("2000")), new TextLiteral("Dune"), new BoolLiteral(true)));

    @Test
    void jdbcRenderingUsesQuestionMarks() {
        assertThat(QUERY.sql()).isEqualTo("select * from book where year >= ? and title = ? and flag = ?");
    }

    @Test
    void customPlaceholderStylesAreOneBased() {
        assertThat(QUERY.render(i -> "?" + i)).isEqualTo("select * from book where year >= ?1 and title = ?2 and flag = ?3");
        assertThat(QUERY.render(i -> "[p" + i + "]")).endsWith("title = [p2] and flag = [p3]");
    }

    @Test
    void valuesArePlainJavaObjects() {
        BoundQuery withNull = BoundQueries.of(List.of("a ", " b ", ""), List.of(new NullLiteral(), new TextLiteral("x")));

        assertThat(QUERY.values()).containsExactly(new BigDecimal("2000"), "Dune", true);
        assertThat(withNull.values()).isEqualTo(Arrays.asList(null, "x"));
    }

    @Test
    void segmentCountMustMatchParameters() {
        assertThatThrownBy(() -> BoundQueries.of(List.of("a ? b"), List.of(new TextLiteral("x"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expected 2 segments for 1 parameters");
    }

    @Test
    void parameterlessQuery() {
        BoundQuery q = BoundQueries.of("select 1");
        assertThat(q.sql()).isEqualTo("select 1");
        assertThat(q.values()).isEmpty();
    }

    @Test
    void inputsAreDefensivelyCopied() {
        List<DslLiteral> params = new ArrayList<>(List.of(new TextLiteral("x")));
        BoundQuery q = BoundQueries.of(List.of("a ", ""), params);
        params.add(new TextLiteral("y"));

        assertThat(q.parameters()).hasSize(1);
        assertThatThrownBy(() -> q.segments().add("z")).isInstanceOf(UnsupportedOperationException.class);
    }
}
