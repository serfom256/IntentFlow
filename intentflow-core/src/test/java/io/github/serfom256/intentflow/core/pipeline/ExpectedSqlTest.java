package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentRelation;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceNames;
import io.github.serfom256.intentflow.core.pipeline.factories.AstValidators;
import io.github.serfom256.intentflow.core.pipeline.factories.DslParsers;
import io.github.serfom256.intentflow.core.pipeline.factories.SqlBuilders;
import io.github.serfom256.intentflow.core.schema.SchemaFacts;
import io.github.serfom256.intentflow.core.schema.TableFacts;
import io.github.serfom256.intentflow.core.sql.Optimization;
import io.github.serfom256.intentflow.dialect.mysql.MySqlDialect;
import io.github.serfom256.intentflow.dialect.postgresql.PostgresDialect;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ExpectedSqlTest {

    enum Category {
        LAPTOP, PHONE, TABLET
    }

    enum Status {
        NEW, PAID, SHIPPED, CANCELLED
    }

    @IntentService(value = "products", table = "product", uri = "/products/{{id}}", defaultSort = "id")
    record Product(@IntentField long id, @IntentField String name, @IntentField(caseSensitive = true) String sku,
            @IntentField Category category, @IntentField BigDecimal price, @IntentField BigDecimal discount,
            @IntentField int stock, @IntentField boolean active, @IntentField Instant createdAt) {
    }

    @IntentService(value = "customers", table = "customer", uri = "/customers/{{id}}", defaultSort = "id")
    @IntentRelation(name = "orders", service = "orders", from = "id", to = "customer_id")
    record Customer(@IntentField long id, @IntentField String name, @IntentField String country,
            @IntentField boolean vip, @IntentField Instant createdAt) {
    }

    @IntentService(value = "orders", table = "orders", uri = "/orders/{{id}}", defaultSort = { "created_at desc",
            "id desc" })
    @IntentRelation(name = "customer", service = "customers", from = "customer_id", to = "id")
    record Order(@IntentField long id, @IntentField long customerId, @IntentField Status status,
            @IntentField BigDecimal total, @IntentField int itemsCount, @IntentField Instant createdAt) {
    }

    static final List<String> QUERIES = List.of(
            "products",
            "products | filter name == \"Galaxy\"",
            "products | filter sku == \"AB-1\"",
            "products | filter category == \"laptop\"",
            "products | filter category in (\"laptop\", \"phone\")",
            "products | filter sku in (\"A\", \"B\")",
            "products | filter name ~ \"50%_off\"",
            "products | filter price >= 10 and price < 99.5 and active == true",
            "products | filter not (stock == 0 or discount == null)",
            "products | filter discount != null",
            "products | filter created_at == \"2026-10-10\"",
            "products | filter created_at != \"2026-10-10\"",
            "products | filter created_at > \"2026-10-10\"",
            "products | filter created_at <= \"2026-10-10\"",
            "products | filter created_at < \"2026-10-10T10:15:00\"",
            "products | filter created_at in (\"2026-10-10\", \"2026-10-12\")",
            "products | filter coalesce(discount, 0) > 10",
            "products | filter price * (1 - coalesce(discount, 0) / 100) < 500",
            "products | filter year(created_at) == 2025 and month(created_at) in (1, 2, 3)",
            "products | filter lower(name) ~ \"pro\"",
            "products | filter length(name) > 10 and round(price) == 100 and abs(stock - 10) < 5",
            "products | filter date_trunc(\"month\", created_at) == \"2026-01-01\"",
            "products | filter created_at::date == \"2026-10-10\"",
            "products | filter cast(sku as integer) > 1000",
            "products | sort price desc, name",
            "products | sort round(price, 2) desc | skip 20 | take 10",
            "products | group category | count",
            "products | group category | avg price | sort avg desc | take 3",
            "products | group year(created_at), category | sum price * stock | sort sum desc",
            "products | filter active == true | max price",
            "products | top 3 per category by price desc | sort category",
            "products | top 5 by price * stock desc",
            "orders",
            "orders | filter customer.vip == true and status == \"paid\"",
            "orders | filter customer.country in (\"DE\", \"FR\") or total > 1000",
            "customers | filter orders.total > 100 and orders.status == \"PAID\" and country == \"DE\"",
            "customers | filter not (orders.status == \"CANCELLED\")",
            "orders | sort customer.name",
            "customers | sort orders.total desc",
            "orders | filter customer.vip == true | top 1 per customer_id by created_at desc",
            "orders | group status | count | sort status",
            "orders | filter created_at >= \"2026-01-01\" | group date_trunc(\"month\", created_at) | sum total",
            "orders | filter status != \"cancelled\" | take 5",
            "customers | filter name ~ \"ann\" and created_at >= \"2025-01-01\" | sort created_at desc",
            "orders | filter customer.name ~ \"o\" and items_count > 2");

    @IntentService(value = "invoices", table = "billing.invoice", uri = "/invoices/{{id}}", defaultSort = "id")
    @IntentRelation(name = "customer", service = "customers", from = "customer_id", to = "id")
    record Invoice(@IntentField long id, @IntentField long customerId, @IntentField BigDecimal amount,
            @IntentField Instant issuedAt) {
    }

    static final List<String> WITH_FACTS = List.of(
            "products",
            "products | filter category == \"laptop\" and name == \"Galaxy\"",
            "products | filter category in (\"laptop\", \"PHONE\")",
            "products | sort created_at desc, price desc",
            "products | top 2 per category by created_at desc",
            "products | group category | count",
            "customers | filter orders.total > 100 and orders.status == \"paid\"",
            "customers | sort orders.total desc",
            "orders | sort customer.name",
            "orders | filter year(created_at) == 2026 and month(created_at) == 3 and day(created_at) == 9",
            "orders | filter year(created_at) in (2024, 2025) or year(created_at) > 2025",
            "invoices | sort customer.name desc",
            "invoices | filter customer.vip == true | sort issued_at desc",
            "keys: products | filter price > 10",
            "keys: invoices | sort customer.name",
            "keys: products | top 1 per category by price desc",
            "keys: products | group category | count");

    private static final ServiceCatalog CATALOG = ServiceCatalogs.fromAnnotated(Product.class, Customer.class,
            Order.class, Invoice.class);

    private static final SchemaFacts FACTS = SchemaFacts.of(Map.of(
            "product", new TableFacts(List.of("id"), List.of(List.of("sku")),
                    Set.of("name", "category", "created_at"), Map.of("category", List.of("LAPTOP", "PHONE", "TABLET"))),
            "customer", new TableFacts(List.of("id"), List.of(), Set.of("name", "country"), Map.of()),
            "orders", new TableFacts(List.of("id"), List.of(), Set.of("created_at", "status"),
                    Map.of("status", List.of("NEW", "PAID", "SHIPPED", "CANCELLED"))),
            "billing.invoice", new TableFacts(List.of("id"), List.of(), Set.of("issued_at"), Map.of())),
            Map.of(ServiceNames.of("products"), "archived = false", ServiceNames.of("orders"), "deleted_at IS NULL"));

    private static SqlBuilder builder(String dialect) {
        return SqlBuilders.create(dialect.equals("postgresql") ? new PostgresDialect() : new MySqlDialect(),
                SchemaFacts.none(), EnumSet.allOf(Optimization.class), ZoneOffset.UTC);
    }

    private static String render(SqlBuilder builder, List<String> queries) {
        StringBuilder out = new StringBuilder();
        for (String line : queries) {
            boolean keys = line.startsWith("keys: ");
            String dsl = keys ? line.substring(6) : line;
            BoundQuery query = builder.build(AstValidators.create()
                    .validate(DslParsers.create().parse(DslSources.of(dsl)), CATALOG),
                    keys ? ResultShape.KEYS : ResultShape.ROWS);
            out.append("-- ").append(line).append('\n')
                    .append(query.sql()).append('\n')
                    .append("binds: ").append(query.values().stream().map(String::valueOf)
                            .collect(Collectors.joining(", ", "[", "]")))
                    .append('\n');
            if (!query.optimizations().isEmpty()) {
                out.append("optimizations: ").append(String.join(", ", query.optimizations())).append('\n');
            }
            out.append('\n');
        }
        return out.toString();
    }

    @ParameterizedTest
    @ValueSource(strings = { "postgresql", "mysql" })
    void generatedSqlMatchesTheExpectedFile(String dialect) throws IOException {
        compare(dialect + ".sql", render(builder(dialect), QUERIES));
    }

    @ParameterizedTest
    @ValueSource(strings = { "postgresql", "mysql" })
    void sqlWithSchemaFactsMatchesTheExpectedFile(String dialect) throws IOException {
        SqlDialect sqlDialect = dialect.equals("postgresql") ? new PostgresDialect() : new MySqlDialect();
        compare(dialect + "-facts.sql", render(SqlBuilders.create(sqlDialect, FACTS, EnumSet.allOf(Optimization.class),
                ZoneOffset.UTC), WITH_FACTS));
    }

    @ParameterizedTest
    @ValueSource(strings = { "postgresql", "mysql" })
    void disabledOptimizationsKeepTheConservativeSql(String dialect) {
        SqlDialect sqlDialect = dialect.equals("postgresql") ? new PostgresDialect() : new MySqlDialect();
        String plain = render(SqlBuilders.create(sqlDialect, SchemaFacts.none(), Set.of()), WITH_FACTS);
        assertThat(plain).doesNotContain("optimizations:").doesNotContain("LEFT JOIN");
    }

    private static void compare(String file, String actual) throws IOException {
        Path expectedFile = Path.of("src/test/resources/expected-sql/" + file);
        if ("true".equals(System.getenv("INTENTFLOW_UPDATE_EXPECTED_SQL")) || !Files.exists(expectedFile)) {
            Files.writeString(expectedFile, actual, StandardCharsets.UTF_8);
        }
        List<String> expected = blocks(Files.readString(expectedFile, StandardCharsets.UTF_8));
        List<String> got = blocks(actual);
        assertThat(got).as("re-run with INTENTFLOW_UPDATE_EXPECTED_SQL=true to accept a deliberate change, "
                + "then review the diff of %s", expectedFile).containsExactlyElementsOf(expected);
    }

    private static List<String> blocks(String text) {
        List<String> out = new ArrayList<>();
        for (String block : text.split("\n\n")) {
            if (!block.isBlank()) {
                out.add(block.strip());
            }
        }
        return out;
    }
}
