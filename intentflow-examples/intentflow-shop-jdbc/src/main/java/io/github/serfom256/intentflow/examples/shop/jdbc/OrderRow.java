package io.github.serfom256.intentflow.examples.shop.jdbc;

import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentRelation;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.examples.shop.domain.Descriptions;
import io.github.serfom256.intentflow.examples.shop.domain.Order;
import io.github.serfom256.intentflow.examples.shop.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@IntentService(value = "orders", table = "orders", uri = "/orders/{{id}}", maxTake = 50, defaultSort = {
        "created_at desc", "id desc" }, description = Descriptions.ORDERS)
@IntentRelation(name = "customer", service = "customers", from = "customer_id", to = "id")
public record OrderRow(@IntentField long id, @IntentField(description = Descriptions.ORDER_CUSTOMER_ID) long customerId,
        @IntentField OrderStatus status, @IntentField(description = Descriptions.ORDER_TOTAL) BigDecimal total,
        @IntentField(description = Descriptions.ORDER_ITEMS_COUNT) int itemsCount,
        @IntentField(description = Descriptions.ORDER_CREATED_AT) OffsetDateTime createdAt) {

    Order toDomain() {
        return new Order(id, customerId, status, total, itemsCount, createdAt);
    }
}
