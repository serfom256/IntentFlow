package io.github.serfom256.intentflow.core.catalog.impl;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class SnakeCaseTest {

    @ParameterizedTest
    @CsvSource({ "id, id", "inStock, in_stock", "createdAt, created_at", "Product, product",
            "ProductRecord, product_record", "HTTPServer, http_server", "externalURL, external_url",
            "productV2, product_v2", "address2, address2", "already_snake, already_snake", "x, x", })
    void convertsJavaNames(String input, String expected) {
        assertThat(AnnotatedServiceReader.snakeCase(input)).isEqualTo(expected);
    }
}
