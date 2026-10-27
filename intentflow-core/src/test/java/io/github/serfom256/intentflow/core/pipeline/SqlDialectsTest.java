package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.pipeline.factories.SqlDialects;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlDialectsTest {

    @Test
    void dialectsOnTheClasspathAreFoundByNameIgnoringCase() {
        assertThat(SqlDialects.named(" Test ").name()).isEqualTo("test");
    }

    @Test
    void missingDialectNamesTheModuleToAdd() {
        assertThatThrownBy(() -> SqlDialects.named("sqlserver")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("available: [test]").hasMessageContaining("intentflow-sqlserver");
        assertThatThrownBy(() -> SqlDialects.named("H2")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("intentflow-h2");
    }
}
