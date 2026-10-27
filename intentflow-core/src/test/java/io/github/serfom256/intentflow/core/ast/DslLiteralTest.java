package io.github.serfom256.intentflow.core.ast;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DslLiteralTest {

    @Test
    void sealedHierarchyIsExhaustiveInSwitch() {
        DslLiteral[] literals = { new TextLiteral("ring"), new NumberLiteral(new BigDecimal("42")),
                new BoolLiteral(true), new NullLiteral(), };
        for (DslLiteral lit : literals) {
            String label = switch (lit) {
            case TextLiteral t -> "text:" + t.value();
            case NumberLiteral n -> "num:" + n.value();
            case BoolLiteral b -> "bool:" + b.value();
            case NullLiteral __ -> "null";
            };
            assertThat(label).isNotBlank();
        }
    }

    @Test
    void nullLiteralHasValueEquality() {
        assertThat(new NullLiteral()).isEqualTo(new NullLiteral());
    }
}
