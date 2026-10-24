package io.github.serfom256.intentflow.dialect.mysql;

import io.github.serfom256.intentflow.core.pipeline.factories.SqlDialects;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MySqlDiscoveryTest {

    @Test
    void theDialectIsFoundByNameOrAlias() {
        assertThat(SqlDialects.named("MySQL").name()).isEqualTo("mysql");
        assertThat(SqlDialects.named("mariadb").name()).isEqualTo("mysql");
    }
}
