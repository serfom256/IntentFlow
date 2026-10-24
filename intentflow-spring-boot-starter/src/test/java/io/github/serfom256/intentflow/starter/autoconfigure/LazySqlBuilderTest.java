package io.github.serfom256.intentflow.starter.autoconfigure;

import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.factories.BoundQueries;
import io.github.serfom256.intentflow.core.pipeline.ResultShape;
import io.github.serfom256.intentflow.core.pipeline.RuntimeRestriction;
import io.github.serfom256.intentflow.core.pipeline.SqlBuilder;
import io.github.serfom256.intentflow.core.pipeline.ValidatedQuery;
import io.github.serfom256.intentflow.dialect.postgresql.PostgresDialect;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class LazySqlBuilderTest {

    private static final BoundQuery AFTER = BoundQueries.of("SELECT 'after'");

    private static final SqlBuilder KEYSET = new SqlBuilder() {
        @Override
        public BoundQuery build(ValidatedQuery query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean supportsKeyset(ValidatedQuery query) {
            return true;
        }

        @Override
        public BoundQuery buildAfter(ValidatedQuery query, ResultShape shape,
                Function<Class<?>, List<RuntimeRestriction>> restrictions, List<Object> after) {
            return AFTER;
        }

        @Override
        public @Nullable Object read(@Nullable Object value) {
            return "read " + value;
        }
    };

    @Test
    void pagingAndReadingReachTheBuiltBuilder() {
        SqlBuilder lazy = new LazySqlBuilder(PostgresDialect::new, dialect -> KEYSET);

        assertThat(lazy.supportsKeyset(null)).isTrue();
        assertThat(lazy.buildAfter(null, ResultShape.ROWS, type -> List.of(), List.of(1))).isSameAs(AFTER);
        assertThat(lazy.read(5)).isEqualTo("read 5");
    }
}
