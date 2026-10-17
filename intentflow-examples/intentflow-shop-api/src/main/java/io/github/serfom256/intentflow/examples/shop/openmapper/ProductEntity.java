package io.github.serfom256.intentflow.examples.shop.openmapper;

import io.github.serfom256.annotations.entity.Field;
import io.github.serfom256.annotations.entity.Model;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.examples.shop.domain.Category;
import io.github.serfom256.intentflow.examples.shop.domain.Descriptions;
import io.github.serfom256.intentflow.examples.shop.domain.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Model(primaryKey = "id")
@IntentService(value = "products", table = "product", uri = "/products/{{id}}", maxTake = 50, defaultSort = "id", description = Descriptions.PRODUCTS)
public class ProductEntity {

    @Field
    @IntentField
    private @Nullable Long id;

    @Field
    @IntentField(description = Descriptions.PRODUCT_NAME)
    private String name;

    @Field
    @IntentField(description = Descriptions.PRODUCT_BRAND)
    private String brand;

    @Field
    @IntentField(description = Descriptions.PRODUCT_CATEGORY)
    private Category category;

    @Field
    @IntentField(description = Descriptions.PRODUCT_PRICE)
    private BigDecimal price;

    @Field
    @IntentField(description = Descriptions.PRODUCT_DISCOUNT)
    private @Nullable BigDecimal discount;

    @Field
    @IntentField(description = Descriptions.PRODUCT_RATING)
    private BigDecimal rating;

    @Field
    @IntentField(description = Descriptions.PRODUCT_STOCK)
    private int stock;

    @Field
    @IntentField(description = Descriptions.PRODUCT_ACTIVE)
    private boolean active;

    @Field(name = "supplier_cost")
    private BigDecimal supplierCost;

    @Field(name = "created_at")
    @IntentField(description = Descriptions.PRODUCT_CREATED_AT)
    private OffsetDateTime createdAt;

    static ProductEntity from(Product p) {
        return new ProductEntity(p.getId(), p.getName(), p.getBrand(), p.getCategory(), p.getPrice(), p.getDiscount(),
                p.getRating(), p.getStock(), p.isActive(), p.getSupplierCost(), p.getCreatedAt());
    }

    Product toDomain() {
        return new Product(id, name, brand, category, price, discount, rating, stock, active, supplierCost, createdAt);
    }
}
