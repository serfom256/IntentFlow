package io.github.serfom256.intentflow.examples.shop.jpa;

import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.examples.shop.domain.Descriptions;
import io.github.serfom256.intentflow.examples.shop.domain.Order;
import io.github.serfom256.intentflow.examples.shop.domain.OrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "orders")
@IntentService(value = "orders", uri = "/orders/{{id}}", maxTake = 50, defaultSort = { "created_at desc",
        "id desc" }, description = Descriptions.ORDERS)
@Getter
@Setter
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @IntentField
    private @Nullable Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private CustomerEntity customer;

    @Column(name = "customer_id", insertable = false, updatable = false)
    @IntentField(description = Descriptions.ORDER_CUSTOMER_ID)
    private @Nullable Long customerId;

    @Enumerated(EnumType.STRING)
    @IntentField
    private OrderStatus status;

    @IntentField(description = Descriptions.ORDER_TOTAL)
    private BigDecimal total;

    @IntentField(description = Descriptions.ORDER_ITEMS_COUNT)
    private int itemsCount;

    @IntentField(description = Descriptions.ORDER_CREATED_AT)
    private OffsetDateTime createdAt;

    void apply(Order o, CustomerEntity owner) {
        customer = owner;
        status = o.getStatus();
        total = o.getTotal();
        itemsCount = o.getItemsCount();
        createdAt = o.getCreatedAt();
    }

    Order toDomain() {
        return new Order(id, customer.getId(), status, total, itemsCount, createdAt);
    }
}
