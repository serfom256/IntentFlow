package io.github.serfom256.intentflow.examples.shop;

import com.jayway.jsonpath.JsonPath;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@ActiveProfiles("mysql")
@Import(MySqlContainerConfig.class)
class MySqlPerformanceTest extends PerformanceSupport {

    private static final Set<String> TABLES = Set.of("customer", "orders", "product");

    @Override
    void scaleUp() {
        jdbc.sql("CREATE TABLE perf_seq (n INT PRIMARY KEY)").update();
        jdbc.sql("""
                INSERT INTO perf_seq (n)
                WITH RECURSIVE s (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM s WHERE n < 1000) SELECT n FROM s
                """).update();
        jdbc.sql("""
                INSERT INTO customer (full_name, email, city, country, vip, created_at)
                SELECT CONCAT('Perf Customer ', n), CONCAT('perf.', n, '@mail.example'), 'Perf City',
                       ELT(1 + n % 10, 'DE', 'FR', 'GB', 'US', 'ES', 'IT', 'NL', 'PL', 'JP', 'BR'), n % 50 = 0,
                       TIMESTAMP '2024-01-01 00:00:00' + INTERVAL (n * 7919 % 24000) HOUR
                FROM %s
                """.replace("%s", numbers(CUSTOMERS))).update();
        long customers = jdbc.sql("SELECT MAX(id) FROM customer").query(Long.class).single();
        jdbc.sql("""
                INSERT INTO orders (customer_id, status, total, items_count, created_at)
                SELECT 1 + n * 104729 %% %d,
                       ELT(1 + n %% 12, 'DELIVERED', 'DELIVERED', 'DELIVERED', 'DELIVERED', 'PAID', 'PAID', 'PAID',
                           'SHIPPED', 'SHIPPED', 'NEW', 'NEW', 'CANCELLED'),
                       1 + (n * 7919 %% 500000) / 100, 1 + n %% 8,
                       TIMESTAMP '2024-01-01 00:00:00' + INTERVAL (n * 7919 %% 24600) HOUR
                FROM %s
                """.formatted(customers, numbers(ORDERS))).update();
        jdbc.sql("""
                INSERT INTO product (name, brand, category, price, rating, stock, active, supplier_cost, created_at)
                SELECT CONCAT(ELT(1 + n % 10, 'MacBook', 'Galaxy', 'XPS', 'ThinkPad', 'EliteBook', 'WH', 'UltraGear',
                                  'Zenbook', 'Pixel', 'MX'), ' ', n),
                       ELT(1 + n % 10, 'Apple', 'Samsung', 'Dell', 'Lenovo', 'HP', 'Sony', 'LG', 'ASUS', 'Google',
                           'Logitech'),
                       ELT(1 + n % 6, 'LAPTOP', 'PHONE', 'TABLET', 'HEADPHONES', 'MONITOR', 'ACCESSORY'),
                       10 + (n * 7919 % 300000) / 100, 2.5 + (n % 26) / 10, n % 200, n % 13 <> 0,
                       (10 + (n * 7919 % 300000) / 100) * 0.6,
                       TIMESTAMP '2024-01-01 00:00:00' + INTERVAL (n * 7919 % 24000) HOUR
                FROM %s
                """.replace("%s", numbers(PRODUCTS))).update();
        jdbc.sql("DROP TABLE perf_seq").update();
        jdbc.sql("CREATE INDEX perf_orders_created_idx ON orders (created_at, id)").update();
        jdbc.sql("CREATE INDEX perf_orders_total_idx ON orders (total)").update();
        jdbc.sql("CREATE INDEX perf_product_price_idx ON product (price)").update();
        jdbc.sql("ANALYZE TABLE customer, orders, product").query().listOfRows();
    }

    private static String numbers(int rows) {
        return ("(SELECT (c.n - 1) * 1000000 + (a.n - 1) * 1000 + b.n AS n FROM perf_seq a CROSS JOIN perf_seq b "
                + "CROSS JOIN perf_seq c WHERE c.n <= %d) s WHERE s.n <= %d")
                .formatted((rows + 999_999) / 1_000_000, rows);
    }

    @Override
    boolean reliableCost() {
        return false;
    }

    @Override
    String handWritten(Case test) {
        return test.mysql();
    }

    @Override
    Plan explain(String sql, List<Object> parameters) {
        String json = jdbc.sql("EXPLAIN FORMAT=JSON " + sql).params(parameters).query(String.class).single();
        String cost = JsonPath.read(json, "$.query_block.cost_info.query_cost");
        List<String> scans = JsonPath.read(json, "$..[?(@.access_type == 'ALL')].table_name");
        Set<String> tables = new TreeSet<>(scans);
        tables.retainAll(TABLES);
        return new Plan(Double.parseDouble(cost), tables, json);
    }
}
