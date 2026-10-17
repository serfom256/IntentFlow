package io.github.serfom256.intentflow.examples.shop.jpa;

import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.examples.shop.domain.Category;
import io.github.serfom256.intentflow.examples.shop.domain.Descriptions;
import io.github.serfom256.intentflow.examples.shop.domain.Product;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "product")
@IntentService(value = "products", uri = "/products/{{id}}", maxTake = 50, defaultSort = "id", description = Descriptions.PRODUCTS)
@Getter
@Setter
public class ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @IntentField
    private @Nullable Long id;

    @IntentField(description = Descriptions.PRODUCT_NAME)
    private String name;

    @IntentField(description = Descriptions.PRODUCT_BRAND)
    private String brand;

    @Enumerated(EnumType.STRING)
    @IntentField(description = Descriptions.PRODUCT_CATEGORY)
    private Category category;

    @IntentField(description = Descriptions.PRODUCT_PRICE)
    private BigDecimal price;

    @IntentField(description = Descriptions.PRODUCT_DISCOUNT)
    private @Nullable BigDecimal discount;

    @IntentField(description = Descriptions.PRODUCT_RATING)
    private BigDecimal rating;

    @IntentField(description = Descriptions.PRODUCT_STOCK)
    private int stock;

    @IntentField(description = Descriptions.PRODUCT_ACTIVE)
    private boolean active;

    private BigDecimal supplierCost;

    @IntentField(description = Descriptions.PRODUCT_CREATED_AT)
    private OffsetDateTime createdAt;

    void apply(Product p) {
        name = p.getName();
        brand = p.getBrand();
        category = p.getCategory();
        price = p.getPrice();
        discount = p.getDiscount();
        rating = p.getRating();
        stock = p.getStock();
        active = p.isActive();
        supplierCost = p.getSupplierCost();
        createdAt = p.getCreatedAt();
    }

    Product toDomain() {
        return new Product(id, name, brand, category, price, discount, rating, stock, active, supplierCost, createdAt);
    }
}
