package io.github.serfom256.intentflow.core.dsl;

import io.github.serfom256.intentflow.core.dsl.factories.Positions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PositionTest {

    @Test
    void holdsOffsetLineAndColumn() {
        Position p = Positions.of(42, 5, 12);
        assertThat(p.offset()).isEqualTo(42);
        assertThat(p.line()).isEqualTo(5);
        assertThat(p.column()).isEqualTo(12);
    }

    @Test
    void allowsZeroOffsetAndUnitLineColumn() {
        Position p = Positions.of(0, 1, 1);
        assertThat(p.offset()).isZero();
        assertThat(p.line()).isEqualTo(1);
        assertThat(p.column()).isEqualTo(1);
    }

    @Test
    void rejectsNegativeOffset() {
        assertThatThrownBy(() -> Positions.of(-1, 1, 1)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("offset");
    }

    @Test
    void rejectsZeroLine() {
        assertThatThrownBy(() -> Positions.of(0, 0, 1)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("line");
    }

    @Test
    void rejectsZeroColumn() {
        assertThatThrownBy(() -> Positions.of(0, 1, 0)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("column");
    }
}
