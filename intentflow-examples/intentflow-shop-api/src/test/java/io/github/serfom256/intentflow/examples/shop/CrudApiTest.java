package io.github.serfom256.intentflow.examples.shop;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CrudApiTest extends ApiTestSupport {

    private static String product(String name, String price) {
        return """
                {"name": "%s", "brand": "Acme", "category": "ACCESSORY", "price": %s,
                 "rating": 4.2, "stock": 5, "active": true, "supplierCost": 3.50}
                """.formatted(name, price);
    }

    private long createdId(MvcTestResult result) {
        assertThat(result).hasStatus(HttpStatus.CREATED);
        Number id = JsonPath.read(body(result), "$.id");
        assertThat(result.getResponse().getHeader("Location")).endsWith("/" + id);
        return id.longValue();
    }

    @Nested
    class Products {

        @Test
        void listIsPagedAndSortable() {
            assertThat(mvc.get().uri("/api/products?page=0&size=5")).hasStatusOk().bodyJson()
                    .hasPathSatisfying("$.items", items -> assertThat(items).asArray().hasSize(5))
                    .hasPathSatisfying("$.size", size -> assertThat(size).asNumber().isEqualTo(5))
                    .hasPathSatisfying("$.totalItems",
                            t -> assertThat(t).asNumber()
                                    .satisfies(n -> assertThat(n.intValue()).isGreaterThanOrEqualTo(40)))
                    .extractingPath("$.items[0].name").isEqualTo("MacBook Air 13 M3");
        }

        @Test
        void getSeededProduct() {
            assertThat(mvc.get().uri("/api/products/1")).hasStatusOk().bodyJson().extractingPath("$.name")
                    .isEqualTo("MacBook Air 13 M3");
        }

        @Test
        void createReadUpdateDelete() {
            long id = createdId(post("/api/products", product("Widget " + UUID.randomUUID(), "19.99")));

            assertThat(mvc.get().uri("/api/products/{id}", id)).hasStatusOk().bodyJson().extractingPath("$.price")
                    .asNumber().satisfies(p -> assertThat(p.doubleValue()).isEqualTo(19.99));

            assertThat(put("/api/products/" + id, product("Widget Pro", "24.50"))).hasStatusOk().bodyJson()
                    .extractingPath("$.name").isEqualTo("Widget Pro");
            assertThat(mvc.get().uri("/api/products/{id}", id)).bodyJson().extractingPath("$.createdAt").isNotNull();

            assertThat(mvc.delete().uri("/api/products/{id}", id)).hasStatus(HttpStatus.NO_CONTENT);
            assertThat(mvc.get().uri("/api/products/{id}", id)).hasStatus(HttpStatus.NOT_FOUND);
            assertThat(mvc.delete().uri("/api/products/{id}", id)).hasStatus(HttpStatus.NOT_FOUND);
        }

        @Test
        void validationErrorsAreReportedPerField() {
            String invalid = """
                    {"name": " ", "brand": "Acme", "category": "ACCESSORY", "price": -1,
                     "rating": 7.5, "stock": -3, "active": true, "supplierCost": null}
                    """;
            assertThat(post("/api/products", invalid)).hasStatus(HttpStatus.BAD_REQUEST).bodyJson()
                    .hasPathSatisfying("$.title", t -> assertThat(t).isEqualTo("Invalid request"))
                    .extractingPath("$.errors").asMap()
                    .containsOnlyKeys("name", "price", "rating", "stock", "supplierCost");
        }

        @Test
        void unknownEnumValueIsABadRequest() {
            assertThat(post("/api/products", product("x", "1").replace("ACCESSORY", "TOASTER")))
                    .hasStatus(HttpStatus.BAD_REQUEST).hasContentTypeCompatibleWith("application/problem+json");
        }

        @Test
        void missingProductIsAProblemDetail() {
            assertThat(mvc.get().uri("/api/products/999999")).hasStatus(HttpStatus.NOT_FOUND).bodyJson()
                    .hasPathSatisfying("$.title", t -> assertThat(t).isEqualTo("Product not found"))
                    .extractingPath("$.detail").isEqualTo("Product 999999 not found");
        }
    }

    @Nested
    class Customers {

        @Test
        void createNormalizesEmailAndCountry() {
            String email = "Ada." + UUID.randomUUID() + "@Example.COM";
            MvcTestResult created = post("/api/customers", """
                    {"name": "  Ada Lovelace ", "email": "%s", "city": "London", "country": "gb", "vip": true}
                    """.formatted(email));
            long id = createdId(created);

            assertThat(mvc.get().uri("/api/customers/{id}", id)).bodyJson()
                    .hasPathSatisfying("$.fullName", n -> assertThat(n).isEqualTo("Ada Lovelace"))
                    .hasPathSatisfying("$.country", c -> assertThat(c).isEqualTo("GB")).extractingPath("$.email")
                    .isEqualTo(email.toLowerCase());
        }

        @Test
        void duplicateEmailIsAConflict() {
            String body = """
                    {"name": "Twin", "email": "twin-%s@example.com", "city": "Oslo", "country": "NO", "vip": false}
                    """.formatted(UUID.randomUUID());
            createdId(post("/api/customers", body));
            assertThat(post("/api/customers", body)).hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.title")
                    .isEqualTo("Conflicts with existing data");
        }

        @Test
        void invalidEmailAndCountry() {
            assertThat(post("/api/customers", """
                    {"name": "X", "email": "not-an-email", "city": "Y", "country": "Germany", "vip": false}
                    """)).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.errors").asMap()
                    .containsOnlyKeys("email", "country");
        }
    }

    @Nested
    class Orders {

        private String order(long customerId) {
            return """
                    {"customerId": %d, "status": "NEW", "total": 249.00, "itemsCount": 1}
                    """.formatted(customerId);
        }

        @Test
        void orderForUnknownCustomerIsRejected() {
            assertThat(post("/api/orders", order(424242))).hasStatus(HttpStatus.NOT_FOUND).bodyJson()
                    .extractingPath("$.detail").isEqualTo("Customer 424242 not found");
        }

        @Test
        void deletingACustomerCascadesToTheirOrders() {
            long customer = createdId(post("/api/customers", """
                    {"name": "Temp", "email": "temp-%s@example.com", "city": "Rome", "country": "IT", "vip": false}
                    """.formatted(UUID.randomUUID())));
            List<Long> orders = List.of(createdId(post("/api/orders", order(customer))),
                    createdId(post("/api/orders", order(customer))));

            assertThat(mvc.delete().uri("/api/customers/{id}", customer)).hasStatus(HttpStatus.NO_CONTENT);
            for (long id : orders) {
                assertThat(mvc.get().uri("/api/orders/{id}", id)).hasStatus(HttpStatus.NOT_FOUND);
            }
        }

        @Test
        void updateChangesStatus() {
            long id = createdId(post("/api/orders", order(1)));
            assertThat(put("/api/orders/" + id, order(1).replace("NEW", "SHIPPED"))).hasStatusOk().bodyJson()
                    .extractingPath("$.status").isEqualTo("SHIPPED");
        }

        @Test
        void itemsCountMustBePositive() {
            assertThat(post("/api/orders", order(1).replace("\"itemsCount\": 1", "\"itemsCount\": 0")))
                    .hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.errors").asMap()
                    .containsOnlyKeys("itemsCount");
        }
    }
}
