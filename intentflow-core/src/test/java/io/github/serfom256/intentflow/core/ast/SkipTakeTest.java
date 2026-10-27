package io.github.serfom256.intentflow.core.ast;

import io.github.serfom256.intentflow.core.ast.factories.Skips;
import io.github.serfom256.intentflow.core.ast.factories.Takes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SkipTakeTest {

    @Test
    void skipAllowsZero() {
        assertThat(Skips.of(0).value()).isZero();
    }

    @Test
    void skipAllowsPositive() {
        assertThat(Skips.of(40).value()).isEqualTo(40);
    }

    @Test
    void skipRejectsNegative() {
        assertThatThrownBy(() -> Skips.of(-1)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(">= 0");
    }

    @Test
    void takeMustBePositive() {
        assertThat(Takes.of(1).value()).isEqualTo(1);
        assertThatThrownBy(() -> Takes.of(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Takes.of(-5)).isInstanceOf(IllegalArgumentException.class);
    }
}
