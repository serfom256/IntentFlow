package io.github.serfom256.intentflow.examples.shop.domain;

import io.github.serfom256.annotations.entity.Field;
import io.github.serfom256.annotations.entity.Model;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
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
@IntentService(value = "products", table = "product", uri = "/products/{{id}}", maxTake = 50,
        defaultSort = "id", description = "Items sold in the shop")
public class Product {

    @Field
    @IntentField
    private @Nullable Long id;

    @Field
    @IntentField(description = "model name including the product line, e.g. Galaxy A56, iPhone 16 Pro, "
            + "MacBook Air 13 M3; match it with ~")
    private String name;

    @Field
    @IntentField(description = "manufacturer only, e.g. Apple, Samsung; product lines such as Galaxy, iPhone "
            + "or ThinkPad are part of name")
    private String brand;

    @Field
    @IntentField
    private Category category;

    @Field
    @IntentField(description = "retail price in USD")
    private BigDecimal price;

    @Field
    @IntentField(description = "average customer rating from 0 to 5")
    private BigDecimal rating;

    @Field
    @IntentField(description = "units in the warehouse")
    private int stock;

    @Field
    @IntentField(description = "false when the product is no longer sold")
    private boolean active;

    @Field(name = "supplier_cost")
    private BigDecimal supplierCost;

    @Field(name = "created_at")
    @IntentField(description = "when the product was added to the catalog")
    private OffsetDateTime createdAt;
}
