package io.github.serfom256.intentflow.examples.shop.openmapper;

import io.github.serfom256.annotations.entity.Field;
import io.github.serfom256.annotations.entity.Model;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentRelation;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.examples.shop.domain.Descriptions;
import io.github.serfom256.intentflow.examples.shop.domain.Order;
import io.github.serfom256.intentflow.examples.shop.domain.OrderStatus;
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
@IntentService(value = "orders", table = "orders", uri = "/orders/{{id}}", maxTake = 50, defaultSort = {
        "created_at desc", "id desc" }, description = Descriptions.ORDERS)
@IntentRelation(name = "customer", service = "customers", from = "customer_id", to = "id")
public class OrderEntity {

    @Field
    @IntentField
    private @Nullable Long id;

    @Field(name = "customer_id")
    @IntentField(description = Descriptions.ORDER_CUSTOMER_ID)
    private long customerId;

    @Field
    @IntentField
    private OrderStatus status;

    @Field
    @IntentField(description = Descriptions.ORDER_TOTAL)
    private BigDecimal total;

    @Field(name = "items_count")
    @IntentField(description = Descriptions.ORDER_ITEMS_COUNT)
    private int itemsCount;

    @Field(name = "created_at")
    @IntentField(description = Descriptions.ORDER_CREATED_AT)
    private OffsetDateTime createdAt;

    static OrderEntity from(Order o) {
        return new OrderEntity(o.getId(), o.getCustomerId(), o.getStatus(), o.getTotal(), o.getItemsCount(),
                o.getCreatedAt());
    }

    Order toDomain() {
        return new Order(id, customerId, status, total, itemsCount, createdAt);
    }
}
