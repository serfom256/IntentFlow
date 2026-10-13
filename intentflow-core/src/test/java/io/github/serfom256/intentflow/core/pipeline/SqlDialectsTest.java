package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.pipeline.factories.SqlDialects;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlDialectsTest {

    @Test
    void dialectsOnTheClasspathAreFoundByNameOrAlias() {
        assertThat(SqlDialects.named("MySQL").name()).isEqualTo("mysql");
        assertThat(SqlDialects.named("mariadb").name()).isEqualTo("mysql");
        assertThat(SqlDialects.named("postgres").name()).isEqualTo("postgresql");
        assertThat(SqlDialects.named("H2").name()).isEqualTo("postgresql");
    }

    @Test
    void missingDialectNamesTheModuleToAdd() {
        assertThatThrownBy(() -> SqlDialects.named("oracle"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("available: [mysql, postgresql]")
                .hasMessageContaining("intentflow-oracle");
    }
}
