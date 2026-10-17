package io.github.serfom256.intentflow.admin;

import io.github.serfom256.intentflow.core.testing.DifferentialDatabase;
import io.github.serfom256.intentflow.core.testing.TestDatabase;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class IndexAdvisorIT {

    private static DriverManagerDataSource dataSource(DifferentialDatabase db) {
        TestDatabase.Endpoint e = db.endpoint();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(e.url(), e.user(), e.password());
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS adv_product");
            s.execute("CREATE TABLE adv_product (id BIGINT PRIMARY KEY, name VARCHAR(40), category VARCHAR(20), "
                    + "price DECIMAL(10, 2), created_at TIMESTAMP NULL)");
            s.execute("CREATE INDEX adv_product_price ON adv_product (price)");
        } catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
        return dataSource;
    }

    private static String q(DifferentialDatabase db, String identifier) {
        return db == DifferentialDatabase.POSTGRES ? "\"" + identifier + "\"" : "`" + identifier + "`";
    }

    @ParameterizedTest
    @EnumSource(DifferentialDatabase.class)
    void suggestsAnIndexOnTheFilteredColumnsOfAFullScan(DifferentialDatabase db) {
        IndexAdvisor advisor = new IndexAdvisor(dataSource(db));
        String table = q(db, "adv_product");

        IndexAdvisor.Advice advice = advisor.advise("SELECT * FROM " + table + " WHERE " + q(db, "category")
                + " = ? ORDER BY " + q(db, "id") + " LIMIT 10", List.of("LAPTOP"));

        assertThat(advice.error()).isNull();
        assertThat(advice.plan()).isNotBlank();
        assertThat(advice.suggestions()).extracting(IndexAdvisor.Suggestion::ddl).anyMatch(ddl -> ddl.contains(
                "ON " + table + " (" + q(db, "category")));
    }

    @ParameterizedTest
    @EnumSource(DifferentialDatabase.class)
    void suggestsExpressionIndexesForCaseInsensitiveMatches(DifferentialDatabase db) {
        IndexAdvisor advisor = new IndexAdvisor(dataSource(db));
        String table = q(db, "adv_product");

        IndexAdvisor.Advice equal = advisor.advise("SELECT * FROM " + table + " WHERE LOWER(" + q(db, "name")
                + ") = LOWER(?)", List.of("Galaxy"));
        IndexAdvisor.Advice contains = advisor.advise("SELECT * FROM " + table + " WHERE LOWER(" + q(db, "name")
                + ") LIKE LOWER(?)", List.of("%pro%"));

        assertThat(equal.suggestions()).extracting(IndexAdvisor.Suggestion::ddl)
                .anyMatch(ddl -> ddl.contains("((lower(" + q(db, "name") + ")))"));
        if (db == DifferentialDatabase.POSTGRES) {
            assertThat(contains.suggestions()).extracting(IndexAdvisor.Suggestion::ddl)
                    .anyMatch(ddl -> ddl.contains("USING gin (lower(\"name\") gin_trgm_ops)"));
        }
    }

    @ParameterizedTest
    @EnumSource(DifferentialDatabase.class)
    void skipsColumnsAnIndexAlreadyCovers(DifferentialDatabase db) {
        IndexAdvisor advisor = new IndexAdvisor(dataSource(db));

        IndexAdvisor.Advice advice = advisor.advise("SELECT * FROM " + q(db, "adv_product") + " WHERE "
                + q(db, "price") + " > ?", List.of(10));

        assertThat(advice.error()).isNull();
        assertThat(advice.suggestions()).noneMatch(s -> s.ddl().contains("(" + q(db, "price") + ")"));
    }
}
