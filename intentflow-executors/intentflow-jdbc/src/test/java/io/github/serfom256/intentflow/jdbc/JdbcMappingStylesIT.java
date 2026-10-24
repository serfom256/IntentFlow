package io.github.serfom256.intentflow.jdbc;

import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.NumberLiteral;
import io.github.serfom256.intentflow.core.ast.TextLiteral;
import io.github.serfom256.intentflow.core.ast.factories.BoundQueries;
import io.github.serfom256.intentflow.core.testing.TestDatabase;
import io.github.serfom256.intentflow.dialect.postgresql.PostgresDatabase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcMappingStylesIT {

    private static final TestDatabase.Endpoint POSTGRES = PostgresDatabase.POSTGRES.endpoint();

    enum Status {
        ACTIVE, RETIRED
    }

    record AsRecord(long id, String fullName, Status status, BigDecimal price, Integer stock, Instant createdAt) {
    }

    static final class AsValueClass {
        private final long id;
        private final String fullName;
        private final Status status;

        AsValueClass(long id, String fullName, Status status) {
            this.id = id;
            this.fullName = fullName;
            this.status = status;
        }
    }

    public static final class AsBean {
        private long id;
        private String fullName;
        private Instant createdAt;

        public void setId(long id) {
            this.id = id;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public void setCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
        }
    }

    private static JdbcQueryExecutor executor;

    @BeforeAll
    static void seed() {
        JdbcClient jdbc = JdbcClient.create(new DriverManagerDataSource(
                POSTGRES.url(), POSTGRES.user(), POSTGRES.password()));
        jdbc.sql("DROP TABLE IF EXISTS item").update();
        jdbc.sql("""
                CREATE TABLE item (id BIGINT, full_name TEXT, status TEXT, price NUMERIC(8,2),
                                   stock INTEGER, created_at TIMESTAMPTZ)
                """).update();
        jdbc.sql("""
                INSERT INTO item VALUES
                    (1, 'Widget', 'ACTIVE', 9.99, NULL, '2026-01-31T10:15:30Z'),
                    (2, 'Gadget', 'RETIRED', NULL, 4, NULL)
                """).update();
        executor = new JdbcQueryExecutor(jdbc);
    }

    private static <T> List<T> all(Class<T> type) {
        return executor.execute(BoundQueries.of("SELECT * FROM item ORDER BY id"), type);
    }

    @Test
    void recordsWithEnumsNullsAndTimestamptz() {
        assertThat(all(AsRecord.class)).containsExactly(
                new AsRecord(1, "Widget", Status.ACTIVE, new BigDecimal("9.99"), null,
                        Instant.parse("2026-01-31T10:15:30Z")),
                new AsRecord(2, "Gadget", Status.RETIRED, null, 4, null));
    }

    @Test
    void constructorBoundValueClasses() {
        AsValueClass first = all(AsValueClass.class).getFirst();
        assertThat(first.id).isEqualTo(1);
        assertThat(first.fullName).isEqualTo("Widget");
        assertThat(first.status).isEqualTo(Status.ACTIVE);
    }

    @Test
    void javaBeansViaSetters() {
        AsBean first = all(AsBean.class).getFirst();
        assertThat(first.id).isEqualTo(1);
        assertThat(first.fullName).isEqualTo("Widget");
        assertThat(first.createdAt).isEqualTo(Instant.parse("2026-01-31T10:15:30Z"));
    }

    @Test
    void parametersAreBoundPositionally() {
        BoundQuery query = BoundQueries.of(
                List.of("SELECT * FROM item WHERE LOWER(full_name) = LOWER(", ") AND id < ", ""),
                List.of(new TextLiteral("GADGET"),
                        new NumberLiteral(BigDecimal.TEN)));
        assertThat(executor.execute(query, AsRecord.class)).extracting(AsRecord::id).containsExactly(2L);
    }
}
