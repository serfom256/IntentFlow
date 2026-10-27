package io.github.serfom256.intentflow.examples.shop;

import io.github.serfom256.intentflow.core.api.IntentTrace;
import io.github.serfom256.intentflow.core.api.factories.IntentRequests;
import io.github.serfom256.intentflow.core.pipeline.IntentPipeline;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Tag("performance")
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(ApiTestSupport.ScriptedLlmConfig.class)
abstract class PerformanceSupport {

    static final int CUSTOMERS = 200_000;
    static final int ORDERS = 2_000_000;
    static final int PRODUCTS = 100_000;

    static final double COST_MARGIN = 1.5;
    static final double TIME_MARGIN = 2;
    static final double TIME_SLACK_MS = 5;
    static final int RUNS = 5;

    @Autowired
    IntentPipeline pipeline;
    @Autowired
    ScriptedLlmClient llm;
    @Autowired
    JdbcClient jdbc;

    record Case(String dsl, String postgres, String mysql) {

        static Case of(String dsl, String sql) {
            return new Case(dsl, sql, sql);
        }
    }

    record Plan(double cost, Set<String> fullScans, String text) {
    }

    record Result(Case test, Plan generated, Plan handWritten, double generatedMs, double handWrittenMs,
            List<String> problems) {
    }

    abstract void scaleUp();

    abstract String handWritten(Case test);

    abstract Plan explain(String sql, List<Object> parameters);

    abstract boolean reliableCost();

    @BeforeAll
    void seed() {
        long start = System.nanoTime();
        scaleUp();
        log.info("Seeded {} customers, {} orders, {} products in {} s", CUSTOMERS, ORDERS, PRODUCTS,
                (System.nanoTime() - start) / 1_000_000_000);
    }

    List<Case> cases() {
        String recent = "ORDER BY created_at DESC, id DESC";
        return List.of(
                Case.of("orders | filter customer_id == 4242",
                        "SELECT * FROM orders WHERE customer_id = 4242 " + recent + " LIMIT 50"),
                Case.of("orders | take 20", "SELECT * FROM orders " + recent + " LIMIT 20"),
                Case.of("orders | filter total > 4000 | sort total desc | take 10",
                        "SELECT * FROM orders WHERE total > 4000 ORDER BY total DESC, id DESC LIMIT 10"),
                new Case("orders | filter created_at >= \"2025-03-01\" and created_at < \"2025-04-01\"",
                        "SELECT * FROM orders WHERE created_at >= TIMESTAMPTZ '2025-03-01 00:00:00+00' "
                                + "AND created_at < TIMESTAMPTZ '2025-04-01 00:00:00+00' " + recent + " LIMIT 50",
                        "SELECT * FROM orders WHERE created_at >= TIMESTAMP '2025-03-01 00:00:00' "
                                + "AND created_at < TIMESTAMP '2025-04-01 00:00:00' " + recent + " LIMIT 50"),
                new Case("orders | filter year(created_at) == 2025 and month(created_at) == 3",
                        "SELECT * FROM orders WHERE created_at >= TIMESTAMPTZ '2025-03-01 00:00:00+00' "
                                + "AND created_at < TIMESTAMPTZ '2025-04-01 00:00:00+00' " + recent + " LIMIT 50",
                        "SELECT * FROM orders WHERE created_at >= TIMESTAMP '2025-03-01 00:00:00' "
                                + "AND created_at < TIMESTAMP '2025-04-01 00:00:00' " + recent + " LIMIT 50"),
                new Case("orders | filter created_at == \"2025-03-15\"",
                        "SELECT * FROM orders WHERE created_at >= TIMESTAMPTZ '2025-03-15 00:00:00+00' "
                                + "AND created_at < TIMESTAMPTZ '2025-03-16 00:00:00+00' " + recent + " LIMIT 50",
                        "SELECT * FROM orders WHERE created_at >= TIMESTAMP '2025-03-15 00:00:00' "
                                + "AND created_at < TIMESTAMP '2025-03-16 00:00:00' " + recent + " LIMIT 50"),
                new Case("orders | filter status in (\"NEW\", \"CANCELLED\") | take 20",
                        "SELECT * FROM orders WHERE LOWER(status) IN ('new', 'cancelled') " + recent + " LIMIT 20",
                        "SELECT * FROM orders WHERE status IN ('NEW', 'CANCELLED') " + recent + " LIMIT 20"),
                new Case("orders | filter customer.country == \"JP\" | take 20",
                        "SELECT * FROM orders WHERE customer_id IN "
                                + "(SELECT id FROM customer WHERE LOWER(country) = 'jp') " + recent + " LIMIT 20",
                        "SELECT * FROM orders WHERE customer_id IN " + "(SELECT id FROM customer WHERE country = 'JP') "
                                + recent + " LIMIT 20"),
                Case.of("orders | filter customer.vip == true and total > 4000 | sort total desc | take 10",
                        "SELECT * FROM orders WHERE total > 4000 AND customer_id IN "
                                + "(SELECT id FROM customer WHERE vip) ORDER BY total DESC, id DESC LIMIT 10"),
                Case.of("customers | filter orders.total > 6300",
                        "SELECT * FROM customer c WHERE EXISTS "
                                + "(SELECT 1 FROM orders o WHERE o.customer_id = c.id AND o.total > 6300) "
                                + "ORDER BY id LIMIT 50"),
                new Case("products | filter brand == \"Apple\"",
                        "SELECT * FROM product WHERE LOWER(brand) = 'apple' ORDER BY id LIMIT 50",
                        "SELECT * FROM product WHERE brand = 'Apple' ORDER BY id LIMIT 50"),
                new Case("products | filter category == \"LAPTOP\" | sort price desc | take 10",
                        "SELECT * FROM product WHERE LOWER(category) = 'laptop' ORDER BY price DESC, id LIMIT 10",
                        "SELECT * FROM product WHERE category = 'LAPTOP' ORDER BY price DESC, id LIMIT 10"),
                Case.of("products | filter price < 20 | sort price | take 10",
                        "SELECT * FROM product WHERE price < 20 ORDER BY price, id LIMIT 10"),
                new Case("products | filter name ~ \"thinkpad 77\"",
                        "SELECT * FROM product WHERE name ILIKE '%thinkpad 77%' ORDER BY id LIMIT 50",
                        "SELECT * FROM product WHERE LOWER(name) LIKE '%thinkpad 77%' ORDER BY id LIMIT 50"),
                Case.of("orders | group status | count",
                        "SELECT status, COUNT(*) FROM orders GROUP BY status ORDER BY COUNT(*) DESC, status LIMIT 50"),
                new Case(
                        "orders | filter created_at >= \"2026-10-01\" | group customer_id | sum total "
                                + "| sort sum desc | take 10",
                        "SELECT customer_id, SUM(total) FROM orders WHERE created_at >= TIMESTAMPTZ "
                                + "'2026-10-01 00:00:00+00' GROUP BY customer_id ORDER BY SUM(total) DESC, "
                                + "customer_id LIMIT 10",
                        "SELECT customer_id, SUM(total) FROM orders WHERE created_at >= TIMESTAMP "
                                + "'2026-10-01 00:00:00' GROUP BY customer_id ORDER BY SUM(total) DESC, "
                                + "customer_id LIMIT 10"),
                Case.of("products | top 1 per category by price desc",
                        "SELECT * FROM (SELECT product.*, ROW_NUMBER() OVER "
                                + "(PARTITION BY category ORDER BY price DESC, id) AS rn FROM product) ranked "
                                + "WHERE rn = 1 ORDER BY id LIMIT 50"));
    }

    @Test
    void generatedSqlIsAsFastAsHandWrittenSql() {
        List<Result> results = cases().stream().map(this::measure).toList();

        log.info("SQL performance: {}/{} cases as fast as hand-written SQL",
                results.stream().filter(r -> r.problems().isEmpty()).count(), results.size());
        for (Result r : results) {
            log.info(String.format(Locale.ROOT, "  %-90s cost %10.1f vs %10.1f  time %8.1f vs %8.1f ms  %s",
                    r.test().dsl(), r.generated().cost(), r.handWritten().cost(), r.generatedMs(), r.handWrittenMs(),
                    r.problems().isEmpty() ? "OK" : "SLOW " + r.problems()));
        }
        assertThat(results).allSatisfy(r -> assertThat(r.problems()).as(r.test().dsl()).isEmpty());
    }

    private Result measure(Case test) {
        llm.answer(test.dsl(), test.dsl());
        IntentTrace trace = pipeline.explain(IntentRequests.of(test.dsl()));
        String hand = handWritten(test);
        log.debug("{}\n  generated:    {} {}\n  hand-written: {}", test.dsl(), trace.query(), trace.parameters(), hand);

        List<String> problems = new ArrayList<>();
        List<Object> generatedRows = firstColumn(trace.query(), trace.parameters());
        List<Object> handRows = firstColumn(hand, List.of());
        if (!generatedRows.equals(handRows)) {
            problems.add("different rows: " + head(generatedRows) + " vs " + head(handRows));
        }
        Plan generated = explain(trace.query(), trace.parameters());
        Plan handWritten = explain(hand, List.of());
        Set<String> extraScans = new TreeSet<>(generated.fullScans());
        extraScans.removeAll(handWritten.fullScans());
        if (!extraScans.isEmpty()) {
            problems.add("full scan of " + extraScans);
        }
        if (reliableCost() && generated.cost() > handWritten.cost() * COST_MARGIN + 1) {
            problems.add(String.format(Locale.ROOT, "cost %.1fx", generated.cost() / handWritten.cost()));
        }
        double generatedMs = medianMs(trace.query(), trace.parameters());
        double handWrittenMs = medianMs(hand, List.of());
        if (generatedMs > handWrittenMs * TIME_MARGIN + TIME_SLACK_MS) {
            problems.add(String.format(Locale.ROOT, "time %.1fx", generatedMs / handWrittenMs));
        }
        if (!problems.isEmpty()) {
            log.info("{}\n  generated:    {}\n  hand-written: {}\n  generated plan:    {}\n  hand-written plan: {}",
                    test.dsl(), trace.query(), hand, generated.text(), handWritten.text());
        }
        return new Result(test, generated, handWritten, generatedMs, handWrittenMs, problems);
    }

    private List<Object> firstColumn(String sql, List<Object> parameters) {
        return jdbc.sql(sql).params(parameters).query((rs, i) -> normalize(rs.getObject(1))).list();
    }

    private static Object normalize(Object value) {
        return value instanceof Number n ? new BigDecimal(n.toString()).stripTrailingZeros() : value.toString();
    }

    private static List<Object> head(List<Object> rows) {
        return rows.subList(0, Math.min(5, rows.size()));
    }

    private double medianMs(String sql, List<Object> parameters) {
        firstColumn(sql, parameters);
        double[] times = new double[RUNS];
        for (int i = 0; i < RUNS; i++) {
            long start = System.nanoTime();
            firstColumn(sql, parameters);
            times[i] = (System.nanoTime() - start) / 1_000_000.0;
        }
        Arrays.sort(times);
        return times[RUNS / 2];
    }
}
