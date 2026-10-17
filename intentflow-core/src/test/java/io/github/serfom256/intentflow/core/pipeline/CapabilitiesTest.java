package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.CastType;
import io.github.serfom256.intentflow.core.ast.DslLiteral;
import io.github.serfom256.intentflow.core.ast.ExprFunction;
import io.github.serfom256.intentflow.core.ast.NumberLiteral;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.pipeline.factories.AstValidators;
import io.github.serfom256.intentflow.core.pipeline.factories.DslParsers;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.dialect.postgresql.PostgresDialect;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CapabilitiesTest {

    @IntentService(value = "books", uri = "/books/{{id}}", defaultSort = "id")
    record Book(@IntentField long id, @IntentField String title, @IntentField boolean available,
            @IntentField int year) {
    }

    private static final ServiceCatalog CATALOG = ServiceCatalogs.fromAnnotated(Book.class);

    private static final class LimitedDialect implements SqlDialect {

        private final PostgresDialect delegate = new PostgresDialect();

        @Override
        public String name() {
            return "limited";
        }

        @Override
        public SqlCapabilities capabilities() {
            return new SqlCapabilities(false, false, 3, 5);
        }

        @Override
        public String quote(String identifier) {
            return delegate.quote(identifier);
        }

        @Override
        public void contains(SqlWriter sql, String column, String text) {
            delegate.contains(sql, column, text);
        }

        @Override
        public void timestamp(SqlWriter sql, DslLiteral value) {
            delegate.timestamp(sql, value);
        }

        @Override
        public void orderBy(SqlWriter sql, Runnable value, SortKey.Direction direction) {
            delegate.orderBy(sql, value, direction);
        }

        @Override
        public void extract(SqlWriter sql, ExprFunction unit, Runnable value) {
            delegate.extract(sql, unit, value);
        }

        @Override
        public void truncate(SqlWriter sql, String unit, Runnable value) {
            delegate.truncate(sql, unit, value);
        }

        @Override
        public void cast(SqlWriter sql, CastType type, Runnable value) {
            delegate.cast(sql, type, value);
        }

        @Override
        public void limit(SqlWriter sql, int limit, int offset) {
            delegate.limit(sql, limit, offset);
        }
    }

    private static BoundQuery sql(String dsl) {
        return SqlBuilders.create(new LimitedDialect()).build(AstValidators.create()
                .validate(DslParsers.create().parse(DslSources.of(dsl)), CATALOG));
    }

    @Test
    void topWithoutWindowFunctionsIsAClearError() {
        assertThatThrownBy(() -> sql("books | top 1 per year by title")).isInstanceOf(ValidationException.class)
                .hasMessageContaining("'top' needs window functions, which limited does not support");
    }

    @Test
    void longInListsAreRejectedForTheDialect() {
        assertThat(sql("books | filter year in (1, 2, 3)").sql()).contains("IN (?, ?, ?)");
        assertThatThrownBy(() -> sql("books | filter year in (1, 2, 3, 4)")).isInstanceOf(ValidationException.class)
                .hasMessageContaining("'in' takes at most 3 values on limited, got 4");
    }

    @Test
    void tooManyParametersAreRejected() {
        assertThatThrownBy(() -> sql("books | filter year == 1 or year == 2 or year == 3 or year == 4 "
                + "or year == 5 or year == 6")).isInstanceOf(ValidationException.class)
                .hasMessageContaining("needs 6 parameters, more than limited accepts (5)");
    }

    @Test
    void booleansAreBoundAsNumbersWithoutNativeBooleans() {
        assertThat(sql("books | filter available == true").parameters())
                .containsExactly(new NumberLiteral(BigDecimal.ONE));
    }

    @Test
    void theValidatorCapsInListsEverywhere() {
        assertThatThrownBy(() -> AstValidators.create(AstValidators.DEFAULT_MAX_SKIP, 2)
                .validate(DslParsers.create().parse(DslSources.of("books | filter year in (1, 2, 3)")), CATALOG))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("'in' takes at most 2 values, got 3");
    }
}
