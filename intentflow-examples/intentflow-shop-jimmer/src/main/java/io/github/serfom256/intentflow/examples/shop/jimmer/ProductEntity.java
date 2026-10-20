package io.github.serfom256.intentflow.examples.shop.jimmer;

import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.examples.shop.domain.Category;
import io.github.serfom256.intentflow.examples.shop.domain.Descriptions;
import org.babyfish.jimmer.sql.Entity;
import org.babyfish.jimmer.sql.GeneratedValue;
import org.babyfish.jimmer.sql.GenerationType;
import org.babyfish.jimmer.sql.Id;
import org.babyfish.jimmer.sql.Table;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "product")
@IntentService(value = "products", uri = "/products/{{id}}", maxTake = 50, defaultSort = "id", description = Descriptions.PRODUCTS)
public interface ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @IntentField
    long id();

    @IntentField(description = Descriptions.PRODUCT_NAME)
    String name();

    @IntentField(description = Descriptions.PRODUCT_BRAND)
    String brand();

    @IntentField(description = Descriptions.PRODUCT_CATEGORY)
    Category category();

    @IntentField(description = Descriptions.PRODUCT_PRICE)
    BigDecimal price();

    @IntentField(description = Descriptions.PRODUCT_DISCOUNT)
    @Nullable
    BigDecimal discount();

    @IntentField(description = Descriptions.PRODUCT_RATING)
    BigDecimal rating();

    @IntentField(description = Descriptions.PRODUCT_STOCK)
    int stock();

    @IntentField(description = Descriptions.PRODUCT_ACTIVE)
    boolean active();

    BigDecimal supplierCost();

    @IntentField(description = Descriptions.PRODUCT_CREATED_AT)
    OffsetDateTime createdAt();
}
