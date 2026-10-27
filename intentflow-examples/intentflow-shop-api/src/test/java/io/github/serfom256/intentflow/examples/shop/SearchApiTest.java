package io.github.serfom256.intentflow.examples.shop;

import com.jayway.jsonpath.JsonPath;
import io.github.serfom256.intentflow.core.catalog.FieldMapping;
import io.github.serfom256.intentflow.core.catalog.FieldType;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceNames;
import io.github.serfom256.intentflow.core.pipeline.LlmException;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import io.github.serfom256.intentflow.examples.shop.domain.Category;
import io.github.serfom256.intentflow.examples.shop.domain.Customer;
import io.github.serfom256.intentflow.examples.shop.domain.Order;
import io.github.serfom256.intentflow.examples.shop.domain.OrderStatus;
import io.github.serfom256.intentflow.examples.shop.domain.Product;
import io.github.serfom256.intentflow.examples.shop.store.CrudStore;
import io.github.serfom256.intentflow.openmapper.OpenMapperQueryExecutor;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SearchApiTest extends ApiTestSupport {

    @Autowired
    CrudStore<Product> products;
    @Autowired
    CrudStore<Customer> customers;
    @Autowired
    CrudStore<Order> orders;
    @Autowired
    ServiceCatalog catalog;
    @Autowired
    QueryExecutor executor;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    Environment environment;

    private MvcTestResult search(String question, String dsl) {
        return search("/api/search", question, dsl);
    }

    private MvcTestResult search(String path, String question, String dsl) {
        llm.answer(question, dsl);
        return mvc.get().uri(path).queryParam("q", question).exchange();
    }

    private static List<Long> ids(MvcTestResult result) {
        assertThat(result).hasStatusOk();
        List<Number> ids = JsonPath.read(body(result), "$.items[*].id");
        return ids.stream().map(Number::longValue).toList();
    }

    @Nested
    class Wiring {

        @Test
        void theActiveProfilesOrmRunsTheQueries() throws ClassNotFoundException {
            Map<String, String> executors = Map.of("jpa", "io.github.serfom256.intentflow.jpa.JpaQueryExecutor",
                    "jimmer", "io.github.serfom256.intentflow.jimmer.JimmerQueryExecutor", "jdbc",
                    "io.github.serfom256.intentflow.jdbc.JdbcQueryExecutor");
            String expected = Arrays.stream(environment.getActiveProfiles()).map(executors::get)
                    .filter(Objects::nonNull).findFirst().orElse(OpenMapperQueryExecutor.class.getName());
            assertThat(executor).isInstanceOf(Class.forName(expected));
        }

        @Test
        void annotatedModelsFormTheCatalog() {
            assertThat(catalog.names()).extracting(ServiceName::value).containsExactlyInAnyOrder("products",
                    "customers", "orders");
        }

        @Test
        void columnNamesComeFromTheOrmMapping() {
            Map<String, FieldMapping> p = fields("products");
            assertThat(p.get("created_at").column().value()).isEqualTo("created_at");
            assertThat(fields("customers").get("name").column().value()).isEqualTo("full_name");
            assertThat(fields("orders").get("items_count").column().value()).isEqualTo("items_count");
        }

        @Test
        void sensitiveFieldsAreNotSearchable() {
            assertThat(fields("products")).doesNotContainKeys("supplier_cost", "supplierCost");
            assertThat(fields("customers")).doesNotContainKeys("email");
        }

        @Test
        void typesAndAllowedValuesComeFromJava() {
            Map<String, FieldMapping> p = fields("products");
            assertThat(p.get("category").allowedValues())
                    .containsExactlyElementsOf(Arrays.stream(Category.values()).map(Enum::name).toList());
            assertThat(p.get("price").type()).isEqualTo(FieldType.NUMBER);
            assertThat(p.get("active").type()).isEqualTo(FieldType.BOOLEAN);
            assertThat(p.get("created_at").type()).isEqualTo(FieldType.TIMESTAMP);
        }

        private Map<String, FieldMapping> fields(String service) {
            return catalog.get(ServiceNames.of(service)).fields().stream()
                    .collect(Collectors.toMap(f -> f.logical().value(), Function.identity()));
        }
    }

    @Nested
    class Answers {

        @Test
        void returnsEntitiesWithoutFieldsHiddenFromSearch() {
            Product macbook = products.findById(1).orElseThrow();

            MvcTestResult r = search("the first product", "products | filter id == 1");

            assertThat(r).hasStatusOk().bodyJson()
                    .hasPathSatisfying("$.service", s -> assertThat(s).isEqualTo("products"))
                    .hasPathSatisfying("$.uri", u -> assertThat(u).isEqualTo("/products/1"))
                    .hasPathSatisfying("$.items[0].name", n -> assertThat(n).isEqualTo(macbook.getName()))
                    .hasPathSatisfying("$.items[0].category", c -> assertThat(c).isEqualTo("LAPTOP"))
                    .doesNotHavePath("$.items[0].supplierCost").extractingPath("$.items[0].createdAt").isNotNull();
        }

        @Test
        void filtersCaseInsensitivelyOnTextAndEnumFields() {
            List<Long> expected = products.findAll().stream()
                    .filter(p -> p.getBrand().equals("Apple") && p.getCategory() == Category.LAPTOP).map(Product::getId)
                    .sorted().limit(50).toList();

            MvcTestResult r = search("apple laptops",
                    "products | filter brand == \"apple\" and category == \"laptop\"");

            assertThat(ids(r)).isNotEmpty().containsExactlyElementsOf(expected);
            assertThat(r).bodyJson().extractingPath("$.uri").isEqualTo("/products/" + expected.getFirst());
        }

        @Test
        void sortsAndLimits() {
            List<Long> expected = products.findAll().stream().filter(p -> p.getCategory() == Category.HEADPHONES)
                    .sorted(Comparator.comparing(Product::getRating).reversed().thenComparing(Product::getId)).limit(3)
                    .map(Product::getId).toList();

            assertThat(ids(search("top 3 rated headphones",
                    "products | filter category == \"HEADPHONES\" | sort rating desc, id asc | take 3")))
                    .containsExactlyElementsOf(expected);
        }

        @Test
        void routesToOtherServices() {
            List<Long> expected = customers.findAll().stream()
                    .filter(c -> c.isVip() && List.of("DE", "FR").contains(c.getCountry())).map(Customer::getId)
                    .sorted().toList();

            MvcTestResult r = search("vip customers in germany or france",
                    "customers | filter vip == true and country in (\"de\", \"fr\")");

            assertThat(r).hasStatusOk().bodyJson()
                    .hasPathSatisfying("$.service", s -> assertThat(s).isEqualTo("customers"))
                    .extractingPath("$.items[0].fullName").isNotNull();
            assertThat(ids(r)).containsExactlyElementsOf(expected);
        }

        @Test
        void filtersThroughRelations() {
            Set<Long> vip = customers.findAll().stream().filter(Customer::isVip).map(Customer::getId)
                    .collect(Collectors.toSet());
            List<Long> expected = orders.findAll().stream()
                    .filter(o -> vip.contains(o.getCustomerId()) && o.getStatus() == OrderStatus.DELIVERED)
                    .sorted(Comparator.comparing(Order::getCreatedAt).reversed().thenComparing(Order::getId,
                            Comparator.reverseOrder()))
                    .map(Order::getId).limit(50).toList();

            assertThat(ids(search("delivered orders of vip customers",
                    "orders | filter customer.vip == true and status == \"delivered\"")))
                    .containsExactlyElementsOf(expected);
        }

        @Test
        void fieldsHiddenFromTheLlmAreNotReturnedBySearch() {
            MvcTestResult r = search("/api/products/search", "first apple products",
                    "products | filter brand == \"apple\" | take 3");

            assertThat(r).hasStatusOk().bodyJson().hasPathSatisfying("$.items[0].name", n -> assertThat(n).isNotNull())
                    .doesNotHavePath("$.items[0].supplierCost");
        }

        @Test
        void countsPerGroup() {
            Map<String, Long> expected = products.findAll().stream()
                    .collect(Collectors.groupingBy(p -> p.getCategory().name(), Collectors.counting()));

            MvcTestResult r = search("how many products per category", "products | group category | count");

            assertThat(r).hasStatusOk();
            List<List<Object>> groups = JsonPath.read(body(r), "$.aggregation.rows[*].group");
            List<Number> counts = JsonPath.read(body(r), "$.aggregation.rows[*].value");
            assertThat(groups).hasSize(expected.size());
            for (int i = 0; i < groups.size(); i++) {
                assertThat(counts.get(i).longValue()).isEqualTo(expected.get((String) groups.get(i).getFirst()));
            }
            assertThat(counts.stream().map(Number::longValue).toList()).isSortedAccordingTo(Comparator.reverseOrder());
            assertThat(JsonPath.<List<Object>>read(body(r), "$.items")).isEmpty();
        }

        @Test
        void debugShowsTheDslAndSql() {
            llm.answer("cheap phones", "products | filter category == \"PHONE\" and price < 500");

            MvcTestResult r = mvc.get().uri("/api/search").queryParam("q", "cheap phones").queryParam("debug", "true")
                    .exchange();

            assertThat(r).hasStatusOk().bodyJson()
                    .hasPathSatisfying("$.trace.dsl",
                            d -> assertThat(d).isEqualTo("products | filter category == \"PHONE\" and price < 500"))
                    .hasPathSatisfying("$.trace.sql",
                            sql -> assertThat(sql).asString().startsWith("SELECT ")
                                    .contains(" FROM \"product\" WHERE "))
                    .hasPathSatisfying("$.trace.parameters", p -> assertThat(p).asList().hasSize(2));
            assertThat(search("cheap phones", "products | filter category == \"PHONE\" and price < 500")).bodyJson()
                    .doesNotHavePath("$.trace");
        }

        @Test
        void comparesTimestampsAndNumbers() {
            OffsetDateTime since = OffsetDateTime.parse("2026-01-01T00:00:00Z");
            List<Long> expected = orders.findAll().stream()
                    .filter(o -> o.getStatus() == OrderStatus.DELIVERED
                            && o.getTotal().compareTo(new BigDecimal("1000")) > 0 && !o.getCreatedAt().isBefore(since))
                    .sorted(Comparator.comparing(Order::getCreatedAt).reversed().thenComparing(Order::getId,
                            Comparator.reverseOrder()))
                    .map(Order::getId).limit(50).toList();

            assertThat(ids(search("big delivered orders this year",
                    "orders | filter status == \"delivered\" and total > 1000 and created_at >= \"2026-01-01T00:00:00Z\"")))
                    .containsExactlyElementsOf(expected);
        }

        @Test
        void containsMatchesSubstringsAndEscapesLikeWildcards() {
            List<Long> expected = products.findAll().stream().filter(p -> p.getName().toLowerCase().contains("pro"))
                    .map(Product::getId).sorted().limit(50).toList();

            assertThat(ids(search("anything called pro", "products | filter name ~ \"PRO\"")))
                    .containsExactlyElementsOf(expected);
            assertThat(ids(search("a literal percent sign", "products | filter name ~ \"%\""))).isEmpty();
        }

        @Test
        void emptyResultIsNotAnError() {
            assertThat(search("absurdly expensive products", "products | filter price > 1000000")).hasStatusOk()
                    .bodyJson().hasPathSatisfying("$.items", items -> assertThat(items).asArray().isEmpty())
                    .extractingPath("$.uri").isEqualTo("/products");
        }

        @Test
        void sameQuestionIsTranslatedOnlyOnce() {
            search("in stock monitors", "products | filter category == \"monitor\" and stock > 0");
            int before = llm.calls();

            assertThat(mvc.get().uri("/api/search").queryParam("q", "  in   stock\tmonitors ").exchange())
                    .hasStatusOk();
            assertThat(mvc.get().uri("/api/search").queryParam("q", "in stock monitors").exchange()).hasStatusOk();

            assertThat(llm.calls()).isEqualTo(before);
        }

        @Test
        void injectionInsideLiteralsIsInert() {
            assertThat(ids(search("robert tables", "products | filter name == \"x'; DROP TABLE product; --\"")))
                    .isEmpty();
            assertThat(jdbc.sql("SELECT count(*) FROM product").query(Long.class).single()).isGreaterThan(0);
        }
    }

    @Nested
    class ComplexQueries {

        private static final Comparator<Order> NEWEST_FIRST = Comparator.comparing(Order::getCreatedAt).reversed()
                .thenComparing(Order::getId, Comparator.reverseOrder());

        private static BigDecimal effectivePrice(Product p) {
            BigDecimal discount = p.getDiscount() == null ? BigDecimal.ZERO : p.getDiscount();
            return p.getPrice().multiply(BigDecimal.ONE.subtract(discount.movePointLeft(2)));
        }

        @Test
        void coalesceTreatsMissingDiscountsAsZero() {
            List<Product> phones = products.findAll().stream().filter(p -> p.getCategory() == Category.PHONE).toList();
            List<Long> expected = phones.stream().filter(p -> effectivePrice(p).compareTo(new BigDecimal("500")) < 0)
                    .map(Product::getId).sorted().limit(50).toList();
            assertThat(phones).anySatisfy(p -> {
                assertThat(p.getDiscount()).isNotNull();
                assertThat(p.getPrice()).isGreaterThanOrEqualTo(new BigDecimal("500"));
                assertThat(expected).contains(p.getId());
            });

            assertThat(ids(search("phones under 500 after discount",
                    "products | filter category == \"PHONE\" and price * (1 - coalesce(discount, 0) / 100) < 500")))
                    .containsExactlyElementsOf(expected);
        }

        @Test
        void keepsTheBestRatedProductOfEachCategory() {
            List<Long> expected = products.findAll().stream()
                    .collect(Collectors.groupingBy(Product::getCategory,
                            Collectors.minBy(
                                    Comparator.comparing(Product::getRating).reversed().thenComparing(Product::getId))))
                    .values().stream().map(p -> p.orElseThrow().getId()).sorted().toList();

            MvcTestResult r = search("the best rated product in each category",
                    "products | top 1 per category by rating desc");

            assertThat(ids(r)).hasSize(Category.values().length).containsExactlyElementsOf(expected);
        }

        @Test
        void topByAnExpressionPerBrand() {
            List<Long> expected = products.findAll().stream().filter(p -> p.getCategory() == Category.PHONE)
                    .collect(Collectors.groupingBy(Product::getBrand)).values().stream()
                    .flatMap(list -> list.stream()
                            .sorted(Comparator.comparing(ComplexQueries::effectivePrice).thenComparing(Product::getId))
                            .limit(2))
                    .sorted(Comparator.comparing(Product::getBrand).thenComparing(ComplexQueries::effectivePrice)
                            .thenComparing(Product::getId))
                    .map(Product::getId).toList();

            assertThat(ids(search("the 2 cheapest phones of each brand after discount",
                    "products | filter category == \"PHONE\" "
                            + "| top 2 per brand by price * (1 - coalesce(discount, 0) / 100) "
                            + "| sort brand, price * (1 - coalesce(discount, 0) / 100)")))
                    .containsExactlyElementsOf(expected);
        }

        @Test
        void latestOrderOfEveryVipCustomer() {
            Set<Long> vip = customers.findAll().stream().filter(Customer::isVip).map(Customer::getId)
                    .collect(Collectors.toSet());
            List<Long> expected = orders.findAll().stream().filter(o -> vip.contains(o.getCustomerId()))
                    .collect(Collectors.groupingBy(Order::getCustomerId, Collectors.minBy(NEWEST_FIRST))).values()
                    .stream().map(o -> o.orElseThrow()).sorted(NEWEST_FIRST).map(Order::getId).limit(50).toList();

            assertThat(ids(search("the latest order of every vip customer",
                    "orders | filter customer.vip == true | top 1 per customer_id by created_at desc"))).hasSize(50)
                    .containsExactlyElementsOf(expected);
        }

        @Test
        void sortsByARelatedFieldLikeAJoinWould() {
            List<Long> joined = jdbc
                    .sql("SELECT o.id FROM orders o JOIN customer c ON c.id = o.customer_id "
                            + "WHERE o.status = 'PAID' ORDER BY c.full_name, o.id DESC LIMIT 50")
                    .query(Long.class).list();

            assertThat(ids(search("paid orders sorted by customer name",
                    "orders | filter status == \"PAID\" | sort customer.name"))).containsExactlyElementsOf(joined);
        }

        @Test
        void sortsCustomersByTheirLargestOrderWithoutDuplicates() {
            Map<Long, BigDecimal> largest = orders.findAll().stream()
                    .collect(Collectors.toMap(Order::getCustomerId, Order::getTotal, BigDecimal::max));
            List<Long> expected = largest.entrySet().stream()
                    .filter(e -> e.getValue().compareTo(new BigDecimal("5000")) > 0).sorted(Map.Entry
                            .<Long, BigDecimal>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                    .map(Map.Entry::getKey).limit(50).toList();

            assertThat(ids(search("customers with an order over 5000, biggest order first",
                    "customers | filter orders.total > 5000 | sort orders.total desc"))).doesNotHaveDuplicates()
                    .containsExactlyElementsOf(expected);
        }

        @Test
        void groupsByAnExpression() {
            List<Map<String, Object>> reference = jdbc.sql("SELECT date_trunc('month', created_at) AS month, "
                    + "sum(total) AS revenue FROM orders WHERE created_at >= CAST('2026-01-01' AS TIMESTAMP) "
                    + "GROUP BY 1").query().listOfRows();

            MvcTestResult r = search("revenue per month in 2026", "orders | filter created_at >= \"2026-01-01\" "
                    + "| group date_trunc(\"month\", created_at) | sum total | sort date_trunc(\"month\", created_at)");

            assertThat(r).hasStatusOk();
            List<Number> values = JsonPath.read(body(r), "$.aggregation.rows[*].value");
            assertThat(values).hasSize(reference.size());
            assertThat(values.stream().map(v -> new BigDecimal(v.toString())).reduce(BigDecimal.ZERO, BigDecimal::add))
                    .isEqualByComparingTo(reference.stream().map(m -> (BigDecimal) m.get("revenue"))
                            .reduce(BigDecimal.ZERO, BigDecimal::add));
            assertThat(JsonPath.<List<String>>read(body(r), "$.aggregation.groupBy"))
                    .containsExactly("date_trunc(\"month\", created_at)");
        }
    }

    @Nested
    class ScopedEndpoints {

        @Test
        void productSearchReturnsProducts() {
            List<Long> expected = products.findAll().stream().filter(p -> p.getStock() == 0).map(Product::getId)
                    .sorted().limit(50).toList();

            MvcTestResult r = search("/api/products/search", "scoped: out of stock", "products | filter stock == 0");

            assertThat(ids(r)).containsExactlyElementsOf(expected);
            assertThat(r).bodyJson().extractingPath("$.service").isEqualTo("products");
        }

        @Test
        void theLlmOnlySeesTheScopedServiceAndWhatItRelatesTo() {
            search("/api/products/search", "scoped: anything new", "products | take 1");
            assertThat(llm.lastCatalog()).containsExactly("products");

            search("/api/orders/search", "scoped: anything recent", "orders | take 1");
            assertThat(llm.lastCatalog()).containsExactly("customers", "orders");
        }

        @Test
        void answersForAnotherServiceAreRejected() {
            assertThat(search("/api/products/search", "scoped: actually customers", "customers | take 1"))
                    .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        }

        @Test
        void sameWordsAreCachedPerScope() {
            search("/api/customers/search", "scoped: the newest one", "customers | sort created_at desc | take 1");
            llm.answer("scoped: the newest one", "orders | take 1");

            assertThat(search("/api/orders/search", "scoped: the newest one", "orders | take 1")).hasStatusOk()
                    .bodyJson().extractingPath("$.service").isEqualTo("orders");
        }
    }

    @Nested
    class Failures {

        @Test
        void modelDecliningIsA422WithItsReason() {
            assertThat(search("products by supplier cost", "unsupported \"products have no supplier cost field\""))
                    .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT).bodyJson()
                    .hasPathSatisfying("$.title", t -> assertThat(t).isEqualTo("Can't answer that"))
                    .hasPathSatisfying("$.kind", k -> assertThat(k).isEqualTo("unsupported")).extractingPath("$.detail")
                    .isEqualTo("products have no supplier cost field");
        }

        @Test
        void hiddenFieldIsRejectedEvenIfTheModelTriesIt() {
            assertThat(search("sneaky cost sort", "products | sort supplier_cost desc"))
                    .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT).bodyJson().extractingPath("$.detail").asString()
                    .contains("supplier_cost");
        }

        @Test
        void typeErrorsAreRejected() {
            assertThat(search("price is cheap", "products | filter price == \"cheap\""))
                    .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT).bodyJson().extractingPath("$.detail").asString()
                    .contains("expected a number");
            assertThat(search("toasters", "products | filter category == \"toaster\""))
                    .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT).bodyJson().extractingPath("$.detail").asString()
                    .contains("is not one of");
        }

        @Test
        void unknownServiceAndGarbageDsl() {
            assertThat(search("list invoices", "invoices | take 3")).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
            assertThat(search("model rambles", "Sure! Here is your query: products where cheap"))
                    .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT).bodyJson().extractingPath("$.title")
                    .isEqualTo("Couldn't understand the question");
        }

        @Test
        void llmOutageIsA503() {
            llm.fail("when is the llm down", new LlmException("connection refused"));
            assertThat(mvc.get().uri("/api/search").queryParam("q", "when is the llm down"))
                    .hasStatus(HttpStatus.SERVICE_UNAVAILABLE).bodyJson().extractingPath("$.title")
                    .isEqualTo("Search assistant unavailable");
        }

        @Test
        void blankMissingOrTooLongQueryIsA400() {
            assertThat(mvc.get().uri("/api/search").queryParam("q", "   ")).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(mvc.get().uri("/api/search")).hasStatus(HttpStatus.BAD_REQUEST);
            assertThat(mvc.get().uri("/api/products/search").queryParam("q", "x".repeat(501)))
                    .hasStatus(HttpStatus.BAD_REQUEST);
        }
    }
}
