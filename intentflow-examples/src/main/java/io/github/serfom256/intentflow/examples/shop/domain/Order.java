package io.github.serfom256.intentflow.examples.shop.domain;

import io.github.serfom256.annotations.entity.Field;
import io.github.serfom256.annotations.entity.Model;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentRelation;
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
@IntentService(value = "orders", table = "orders", uri = "/orders/{{id}}", maxTake = 50,
        defaultSort = {"created_at desc", "id desc"}, description = "Customer orders")
@IntentRelation(name = "customer", service = "customers", from = "customer_id", to = "id")
public class Order {

    @Field
    @IntentField
    private @Nullable Long id;

    @Field(name = "customer_id")
    @IntentField(description = "id of the customer who placed the order")
    private long customerId;

    @Field
    @IntentField
    private OrderStatus status;

    @Field
    @IntentField(description = "order total in USD")
    private BigDecimal total;

    @Field(name = "items_count")
    @IntentField(description = "number of items in the order")
    private int itemsCount;

    @Field(name = "created_at")
    @IntentField(description = "when the order was placed")
    private OffsetDateTime createdAt;
}
