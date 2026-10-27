package io.github.serfom256.intentflow.examples.shop;

import com.jayway.jsonpath.JsonPath;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

@Import(PostgresContainerConfig.class)
class PostgresPerformanceTest extends PerformanceSupport {

    @Override
    void scaleUp() {
        jdbc.sql("""
                INSERT INTO customer (full_name, email, city, country, vip, created_at)
                SELECT 'Perf Customer ' || n, 'perf.' || n || '@mail.example', 'Perf City',
                       (ARRAY['DE', 'FR', 'GB', 'US', 'ES', 'IT', 'NL', 'PL', 'JP', 'BR'])[1 + n % 10], n % 50 = 0,
                       TIMESTAMPTZ '2024-01-01 00:00:00+00' + (n * 7919 % 24000) * INTERVAL '1 hour'
                FROM generate_series(1::bigint, :rows) n
                """).param("rows", CUSTOMERS).update();
        long customers = jdbc.sql("SELECT MAX(id) FROM customer").query(Long.class).single();
        jdbc.sql("""
                INSERT INTO orders (customer_id, status, total, items_count, created_at)
                SELECT 1 + n * 104729 % :customers,
                       (ARRAY['DELIVERED', 'DELIVERED', 'DELIVERED', 'DELIVERED', 'PAID', 'PAID', 'PAID', 'SHIPPED',
                              'SHIPPED', 'NEW', 'NEW', 'CANCELLED'])[1 + n % 12],
                       1 + (n * 7919 % 500000) / 100.0, 1 + n % 8,
                       TIMESTAMPTZ '2024-01-01 00:00:00+00' + (n * 7919 % 24600) * INTERVAL '1 hour'
                FROM generate_series(1::bigint, :rows) n
                """).param("customers", customers).param("rows", ORDERS).update();
        jdbc.sql("""
                INSERT INTO product (name, brand, category, price, rating, stock, active, supplier_cost, created_at)
                SELECT (ARRAY['MacBook', 'Galaxy', 'XPS', 'ThinkPad', 'EliteBook', 'WH', 'UltraGear', 'Zenbook',
                              'Pixel', 'MX'])[1 + n % 10] || ' ' || n,
                       (ARRAY['Apple', 'Samsung', 'Dell', 'Lenovo', 'HP', 'Sony', 'LG', 'ASUS', 'Google',
                              'Logitech'])[1 + n % 10],
                       (ARRAY['LAPTOP', 'PHONE', 'TABLET', 'HEADPHONES', 'MONITOR', 'ACCESSORY'])[1 + n % 6],
                       10 + (n * 7919 % 300000) / 100.0, 2.5 + (n % 26) / 10.0, n % 200, n % 13 <> 0,
                       (10 + (n * 7919 % 300000) / 100.0) * 0.6,
                       TIMESTAMPTZ '2024-01-01 00:00:00+00' + (n * 7919 % 24000) * INTERVAL '1 hour'
                FROM generate_series(1::bigint, :rows) n
                """).param("rows", PRODUCTS).update();
        jdbc.sql("CREATE INDEX perf_orders_created_idx ON orders (created_at, id)").update();
        jdbc.sql("CREATE INDEX perf_orders_total_idx ON orders (total)").update();
        jdbc.sql("CREATE INDEX perf_product_price_idx ON product (price)").update();
        jdbc.sql("VACUUM ANALYZE").update();
    }

    @Override
    boolean reliableCost() {
        return true;
    }

    @Override
    String handWritten(Case test) {
        return test.postgres();
    }

    @Override
    Plan explain(String sql, List<Object> parameters) {
        String json = jdbc.sql("EXPLAIN (FORMAT JSON) " + sql).params(parameters).query(String.class).single();
        Number cost = JsonPath.read(json, "$[0].Plan['Total Cost']");
        List<String> scans = JsonPath.read(json, "$..[?(@['Node Type'] =~ /.*Seq Scan/)]['Relation Name']");
        Set<String> tables = new TreeSet<>(scans);
        return new Plan(cost.doubleValue(), tables, json);
    }
}
