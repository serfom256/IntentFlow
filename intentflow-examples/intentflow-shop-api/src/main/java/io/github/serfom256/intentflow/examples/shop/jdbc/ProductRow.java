package io.github.serfom256.intentflow.examples.shop.jdbc;

import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.examples.shop.domain.Category;
import io.github.serfom256.intentflow.examples.shop.domain.Descriptions;
import io.github.serfom256.intentflow.examples.shop.domain.Product;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@IntentService(value = "products", table = "product", uri = "/products/{{id}}", maxTake = 50, defaultSort = "id", description = Descriptions.PRODUCTS)
public record ProductRow(
        @IntentField long id,
        @IntentField(description = Descriptions.PRODUCT_NAME) String name,
        @IntentField(description = Descriptions.PRODUCT_BRAND) String brand,
        @IntentField(description = Descriptions.PRODUCT_CATEGORY) Category category,
        @IntentField(description = Descriptions.PRODUCT_PRICE) BigDecimal price,
        @IntentField(description = Descriptions.PRODUCT_DISCOUNT) @Nullable BigDecimal discount,
        @IntentField(description = Descriptions.PRODUCT_RATING) BigDecimal rating,
        @IntentField(description = Descriptions.PRODUCT_STOCK) int stock,
        @IntentField(description = Descriptions.PRODUCT_ACTIVE) boolean active,
        BigDecimal supplierCost,
        @IntentField(description = Descriptions.PRODUCT_CREATED_AT) OffsetDateTime createdAt) {

    Product toDomain() {
        return new Product(id, name, brand, category, price, discount, rating, stock, active, supplierCost, createdAt);
    }
}
