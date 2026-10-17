package io.github.serfom256.intentflow.examples.shop;

import com.jayway.jsonpath.JsonPath;
import io.github.serfom256.intentflow.examples.shop.domain.Category;
import io.github.serfom256.intentflow.examples.shop.domain.Customer;
import io.github.serfom256.intentflow.examples.shop.domain.Order;
import io.github.serfom256.intentflow.examples.shop.domain.OrderStatus;
import io.github.serfom256.intentflow.examples.shop.domain.Product;
import io.github.serfom256.intentflow.examples.shop.store.CrudStore;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Slf4j
@Tag("llm")
@SpringBootTest(properties = "intentflow.cache.ttl=1s")
@AutoConfigureMockMvc
@Import(LlmEvaluationSupport.FixedClock.class)
abstract class LlmEvaluationSupport {

    static final double PASS_THRESHOLD = 0.8;
    static final int PAGE = 50;
    static final LocalDate TODAY = LocalDate.of(2026, 10, 11);

    @Autowired
    MockMvcTester mvc;
    @Autowired
    CrudStore<Product> products;
    @Autowired
    CrudStore<Customer> customers;
    @Autowired
    CrudStore<Order> orders;
    @Autowired
    JdbcClient jdbc;

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(TODAY.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        }
    }

    @BeforeAll
    static void requireModelServer() {
        String base = System.getProperty("intentflow.llm.base-url",
                System.getenv().getOrDefault("LLM_URL", "http://localhost:1234/v1"));
        try (HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            int status = http.send(HttpRequest.newBuilder(URI.create(base + "/models")).GET().build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode();
            assumeTrue(status == 200, "model server at " + base + " answered " + status);
        } catch (Exception e) {
            assumeTrue(false, "no model server at " + base + ": " + e.getMessage());
        }
    }

    record Case(String language, String question, @Nullable Supplier<List<Long>> expectedIds, boolean ordered,
            @Nullable Supplier<Map<String, BigDecimal>> expectedGroups) {

        static Case set(String q, Supplier<List<Long>> ids) {
            return new Case("en", q, ids, false, null);
        }

        static Case ordered(String q, Supplier<List<Long>> ids) {
            return new Case("en", q, ids, true, null);
        }

        static Case declined(String q) {
            return new Case("en", q, null, false, null);
        }

        static Case groups(String q, Supplier<Map<String, BigDecimal>> groups) {
            return new Case("en", q, null, false, groups);
        }

        Case in(String language) {
            return new Case(language, question, expectedIds, ordered, expectedGroups);
        }
    }

    List<Case> cases() {
        OffsetDateTime threeMonthsAgo = TODAY.minusMonths(3).atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime y2025 = LocalDate.of(2025, 1, 1).atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime y2026 = LocalDate.of(2026, 1, 1).atStartOfDay().atOffset(ZoneOffset.UTC);
        return List.of(
                Case.set("apple laptops under 2000",
                        () -> productIds(p -> p.getBrand().equals("Apple") && p.getCategory() == Category.LAPTOP
                                && p.getPrice().compareTo(new BigDecimal("2000")) < 0)),
                Case.set("which products are out of stock?", () -> productIds(p -> p.getStock() == 0)),
                Case.set("discontinued products", () -> productIds(p -> !p.isActive())),
                Case.set("samsung or google phones", () -> productIds(
                        p -> Set.of("Samsung", "Google").contains(p.getBrand()) && p.getCategory() == Category.PHONE)),
                Case.set("headphones rated 4.5 or higher",
                        () -> productIds(p -> p.getCategory() == Category.HEADPHONES
                                && p.getRating().compareTo(new BigDecimal("4.5")) >= 0)),
                Case.ordered("the 3 most expensive monitors", () -> products.findAll().stream()
                        .filter(p -> p.getCategory() == Category.MONITOR)
                        .sorted(Comparator.comparing(Product::getPrice).reversed())
                        .limit(3).map(Product::getId).toList()),
                Case.set("products added to the catalog in 2025",
                        () -> productIds(p -> !p.getCreatedAt().isBefore(y2025) && p.getCreatedAt().isBefore(y2026))),
                Case.set("vip customers from germany or france", () -> customers.findAll().stream()
                        .filter(c -> c.isVip() && Set.of("DE", "FR").contains(c.getCountry()))
                        .map(Customer::getId).sorted().limit(PAGE).toList()),
                Case.set("customers living in the United States", () -> customers.findAll().stream()
                        .filter(c -> c.getCountry().equals("US")).map(Customer::getId).sorted().limit(PAGE).toList()),
                Case.set("samsung galaxy a56",
                        () -> productIds(p -> p.getName().toLowerCase(Locale.ROOT).contains("galaxy a56"))),
                Case.set("products with ultra in the name",
                        () -> productIds(p -> p.getName().toLowerCase(Locale.ROOT).contains("ultra"))),
                Case.set("cancelled orders over 1000 dollars",
                        () -> orderIds(o -> o.getStatus() == OrderStatus.CANCELLED
                                && o.getTotal().compareTo(new BigDecimal("1000")) > 0)),
                Case.set("orders with exactly 5 items", () -> orderIds(o -> o.getItemsCount() == 5)),
                Case.set("orders placed by customer 5", () -> orderIds(o -> o.getCustomerId() == 5)),
                Case.set("orders placed in the last 3 months",
                        () -> orderIds(o -> !o.getCreatedAt().isBefore(threeMonthsAgo))),
                Case.set("delivered orders from customers in Germany", () -> {
                    Set<Long> german = customers.findAll().stream().filter(c -> c.getCountry().equals("DE"))
                            .map(Customer::getId).collect(Collectors.toSet());
                    return orderIds(o -> german.contains(o.getCustomerId()) && o.getStatus() == OrderStatus.DELIVERED);
                }),
                Case.set("customers who have a cancelled order", () -> {
                    Set<Long> cancelled = orders.findAll().stream().filter(o -> o.getStatus() == OrderStatus.CANCELLED)
                            .map(Order::getCustomerId).collect(Collectors.toSet());
                    return customers.findAll().stream().map(Customer::getId).filter(cancelled::contains).sorted()
                            .limit(PAGE)
                            .toList();
                }),
                Case.groups("how many orders are there per status?", () -> orders.findAll().stream()
                        .collect(Collectors.groupingBy(o -> o.getStatus().name(),
                                Collectors.collectingAndThen(Collectors.counting(), BigDecimal::valueOf)))),
                Case.groups("average order total", () -> Map.of("", orders.findAll().stream().map(Order::getTotal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(orders.findAll().size()), 2, RoundingMode.HALF_UP))),
                Case.set("orders where the average price per item is above 1000 dollars",
                        () -> orderIds(o -> o.getTotal().compareTo(BigDecimal.valueOf(1000L * o.getItemsCount())) > 0)),
                Case.set("orders whose total with 20% tax exceeds 5000", () -> orderIds(
                        o -> o.getTotal().multiply(new BigDecimal("1.2")).compareTo(new BigDecimal("5000")) > 0)),
                Case.set("phones under 500 after discount",
                        () -> productIds(p -> p.getCategory() == Category.PHONE
                                && effectivePrice(p).compareTo(new BigDecimal("500")) < 0)),
                Case.groups("how many discounted products are there per category?", () -> products.findAll().stream()
                        .filter(p -> p.getDiscount() != null)
                        .collect(Collectors.groupingBy(p -> p.getCategory().name(),
                                Collectors.collectingAndThen(Collectors.counting(), BigDecimal::valueOf)))),
                Case.set("the best rated product in each category", () -> products.findAll().stream()
                        .collect(Collectors.groupingBy(Product::getCategory, Collectors.minBy(
                                Comparator.comparing(Product::getRating).reversed().thenComparing(Product::getId))))
                        .values().stream().map(p -> p.orElseThrow().getId()).sorted().toList()),
                Case.set("the latest order of every vip customer", () -> {
                    Set<Long> vip = customers.findAll().stream().filter(Customer::isVip).map(Customer::getId)
                            .collect(Collectors.toSet());
                    Comparator<Order> newest = Comparator.comparing(Order::getCreatedAt).reversed()
                            .thenComparing(Order::getId, Comparator.reverseOrder());
                    return orders.findAll().stream().filter(o -> vip.contains(o.getCustomerId()))
                            .collect(Collectors.groupingBy(Order::getCustomerId, Collectors.minBy(newest)))
                            .values().stream().map(o -> o.orElseThrow()).sorted(newest).map(Order::getId)
                            .limit(PAGE).toList();
                }),
                Case.ordered("customers with an order over 5000, biggest order first", () -> orders.findAll().stream()
                        .collect(Collectors.toMap(Order::getCustomerId, Order::getTotal, BigDecimal::max))
                        .entrySet().stream()
                        .filter(e -> e.getValue().compareTo(new BigDecimal("5000")) > 0)
                        .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed()
                                .thenComparing(Map.Entry.comparingByKey()))
                        .map(Map.Entry::getKey).limit(PAGE).toList()),
                Case.ordered("paid orders sorted by customer name", () -> jdbc.sql(
                        "SELECT o.id FROM orders o JOIN customer c ON c.id = o.customer_id WHERE o.status = 'PAID' "
                                + "ORDER BY c.full_name, o.created_at DESC, o.id DESC LIMIT " + PAGE)
                        .query(Long.class).list()),
                Case.declined("my orders"),
                Case.declined("my orders for October 10"),
                Case.declined("products sorted by supplier cost"),
                Case.declined("customers whose email ends with example.com"),
                Case.declined("which employees work in the warehouse?"));
    }

    @Test
    void answersMatchTheData() {
        List<String> report = new ArrayList<>();
        Map<String, int[]> perLanguage = new TreeMap<>();
        int passed = 0;
        List<Case> all = cases();
        for (Case c : all) {
            String verdict;
            long start = System.nanoTime();
            MvcTestResult r = mvc.get().uri("/api/search").queryParam("q", c.question()).exchange();
            long ms = (System.nanoTime() - start) / 1_000_000;
            int status = r.getResponse().getStatus();
            if (c.expectedIds() == null && c.expectedGroups() == null) {
                verdict = status == 422 ? "PASS" : "FAIL (expected 422, got " + status + ")";
            } else if (status != 200) {
                verdict = "FAIL (HTTP " + status + ": " + body(r) + ")";
            } else if (c.expectedGroups() != null) {
                Map<String, BigDecimal> expected = c.expectedGroups().get();
                Map<String, BigDecimal> got = groups(r);
                boolean ok = got.keySet().equals(expected.keySet()) && expected.entrySet().stream().allMatch(
                        e -> got.get(e.getKey()).subtract(e.getValue()).abs().compareTo(new BigDecimal("0.01")) <= 0);
                verdict = ok ? "PASS" : "FAIL (expected " + expected + ", got " + got + ")";
            } else if (status != 200) {
                verdict = "FAIL (HTTP " + status + ": " + body(r) + ")";
            } else {
                List<Long> expected = c.expectedIds().get();
                List<Number> ids = JsonPath.read(body(r), "$.items[*].id");
                List<Long> got = ids.stream()
                        .map(Number::longValue).toList();
                boolean ok = c.ordered() ? got.equals(expected)
                        : Set.copyOf(got).equals(Set.copyOf(expected)) && got.size() == expected.size();
                verdict = ok ? "PASS" : "FAIL (expected " + expected + ", got " + got + ")";
            }
            int[] score = perLanguage.computeIfAbsent(c.language(), l -> new int[2]);
            score[1]++;
            if (verdict.equals("PASS")) {
                passed++;
                score[0]++;
            }
            report.add("%-3s %-60s %6d ms  %s".formatted(c.language(), c.question(), ms, verdict));
        }
        double accuracy = (double) passed / all.size();
        String summary = perLanguage.entrySet().stream()
                .map(e -> "%s %d/%d (%d%%)".formatted(e.getKey(), e.getValue()[0], e.getValue()[1],
                        Math.round(100.0 * e.getValue()[0] / e.getValue()[1])))
                .collect(Collectors.joining(", "));
        log.info("LLM evaluation: {}/{} ({}%): {}\n{}", passed, all.size(), Math.round(accuracy * 100), summary,
                String.join("\n", report));
        perLanguage.forEach((language, score) -> assertThat((double) score[0] / score[1])
                .as("%s accuracy; %s%n%s", language, summary, String.join("\n", report))
                .isGreaterThanOrEqualTo(PASS_THRESHOLD));
    }

    private static Map<String, BigDecimal> groups(MvcTestResult r) {
        List<List<Object>> groups = JsonPath.read(body(r), "$.aggregation.rows[*].group");
        List<Number> values = JsonPath.read(body(r), "$.aggregation.rows[*].value");
        Map<String, BigDecimal> out = new HashMap<>();
        for (int i = 0; i < groups.size(); i++) {
            out.put(groups.get(i).stream().map(String::valueOf).collect(Collectors.joining("/")),
                    new BigDecimal(values.get(i).toString()));
        }
        return out;
    }

    static BigDecimal effectivePrice(Product p) {
        BigDecimal discount = p.getDiscount() == null ? BigDecimal.ZERO : p.getDiscount();
        return p.getPrice().multiply(BigDecimal.ONE.subtract(discount.movePointLeft(2)));
    }

    List<Long> productIds(Predicate<Product> p) {
        return products.findAll().stream().filter(p).map(Product::getId).sorted().limit(PAGE).toList();
    }

    List<Long> orderIds(Predicate<Order> p) {
        return orders.findAll().stream().filter(p)
                .sorted(Comparator.comparing(Order::getCreatedAt).reversed()
                        .thenComparing(Order::getId, Comparator.reverseOrder()))
                .map(Order::getId).limit(PAGE).toList();
    }

    private static String body(MvcTestResult r) {
        try {
            return r.getResponse().getContentAsString();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
