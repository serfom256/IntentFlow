package io.github.serfom256.intentflow.dialect.postgresql;

import io.github.serfom256.intentflow.core.pipeline.factories.SqlDialects;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostgresDiscoveryTest {

    @Test
    void theDialectIsFoundByNameOrAlias() {
        assertThat(SqlDialects.named("postgres").name()).isEqualTo("postgresql");
        assertThat(SqlDialects.named("PostgreSQL").name()).isEqualTo("postgresql");
    }
}
