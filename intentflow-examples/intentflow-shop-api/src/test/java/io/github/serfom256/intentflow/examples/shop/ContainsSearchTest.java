package io.github.serfom256.intentflow.examples.shop;

import com.jayway.jsonpath.JsonPath;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.pipeline.AstValidator;
import io.github.serfom256.intentflow.core.pipeline.DslParser;
import io.github.serfom256.intentflow.core.pipeline.SqlBuilder;
import io.github.serfom256.intentflow.examples.shop.domain.Customer;
import io.github.serfom256.intentflow.examples.shop.domain.Order;
import io.github.serfom256.intentflow.examples.shop.domain.Product;
import io.github.serfom256.intentflow.examples.shop.store.CrudStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class ContainsSearchTest extends ApiTestSupport {

    @Autowired
    CrudStore<Product> products;
    @Autowired
    CrudStore<Customer> customers;
    @Autowired
    CrudStore<Order> orders;
    @Autowired
    DslParser parser;
    @Autowired
    AstValidator validator;
    @Autowired
    SqlBuilder sqlBuilder;
    @Autowired
    ServiceCatalog catalog;
    @Autowired
    DataSource dataSource;

    @Test
    void demoDataHasProductionLikeVolume() {
        assertThat(products.findAll()).hasSize(1500);
        assertThat(customers.findAll()).hasSize(1000);
        assertThat(orders.findAll()).hasSize(3000);
    }

    @Test
    void containsMatchesAnywhereInTheValueIgnoringCase() {
        List<Long> expected = products.findAll().stream()
                .filter(p -> p.getName().toLowerCase(Locale.ROOT).contains("ultra"))
                .map(Product::getId).sorted().limit(50).toList();

        llm.answer("anything ultra", "products | filter name ~ \"ULTRA\"");
        MvcTestResult r = mvc.get().uri("/api/products/search").queryParam("q", "anything ultra").exchange();

        assertThat(r).hasStatusOk();
        List<Number> ids = JsonPath.read(body(r), "$.items[*].id");
        assertThat(ids.stream().map(Number::longValue).toList()).isNotEmpty().containsExactlyElementsOf(expected);
    }

    @Test
    void containsCountsEveryMatchBeyondThePageLimit() {
        long expected = customers.findAll().stream()
                .filter(c -> c.getFullName().toLowerCase(Locale.ROOT).contains("an")).count();

        llm.answer("how many customers have an in their name", "customers | filter name ~ \"AN\" | count");
        MvcTestResult r = mvc.get().uri("/api/customers/search")
                .queryParam("q", "how many customers have an in their name").exchange();

        assertThat(r).hasStatusOk();
        Number count = JsonPath.read(body(r), "$.aggregation.rows[0].value");
        assertThat(count.longValue()).isEqualTo(expected).isGreaterThan(50);
    }

    @Test
    void generatedContainsPredicateIsServedByTheTrigramIndex() throws SQLException {
        assertThat(plan("products | filter name ~ \"galaxy\"")).contains("product_name_trgm_idx");
        assertThat(plan("customers | filter name ~ \"tanaka\"")).contains("customer_full_name_trgm_idx");
    }

    private String plan(String dsl) throws SQLException {
        BoundQuery query = sqlBuilder.build(validator.validate(parser.parse(DslSources.of(dsl)), catalog));
        assertThat(query.sql()).contains("ILIKE ?");
        try (Connection connection = dataSource.getConnection()) {
            try (Statement s = connection.createStatement()) {
                s.execute("SET enable_seqscan = off");
            }
            try (PreparedStatement explain = connection.prepareStatement("EXPLAIN " + query.sql())) {
                List<Object> values = query.values();
                for (int i = 0; i < values.size(); i++) {
                    explain.setObject(i + 1, values.get(i));
                }
                StringBuilder plan = new StringBuilder();
                try (ResultSet rs = explain.executeQuery()) {
                    while (rs.next()) {
                        plan.append(rs.getString(1)).append('\n');
                    }
                }
                return plan.toString();
            } finally {
                try (Statement s = connection.createStatement()) {
                    s.execute("RESET enable_seqscan");
                }
            }
        }
    }
}
