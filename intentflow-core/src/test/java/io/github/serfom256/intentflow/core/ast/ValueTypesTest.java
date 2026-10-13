package io.github.serfom256.intentflow.core.ast;

import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.ast.factories.AstQueries;
import io.github.serfom256.intentflow.core.ast.factories.BoundQueries;
import io.github.serfom256.intentflow.core.ast.factories.Skips;
import io.github.serfom256.intentflow.core.ast.factories.Takes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueTypesTest {

    @Test
    void dslSourceHoldsValue() {
        assertThat(DslSources.of("books | take 10").value()).isEqualTo("books | take 10");
    }

    @Test
    void dslSourceRejectsBlank() {
        assertThatThrownBy(() -> DslSources.of(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

}
