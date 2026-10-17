package io.github.serfom256.intentflow.examples.shop.jimmer;

import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.examples.shop.domain.Descriptions;
import io.github.serfom256.intentflow.examples.shop.domain.OrderStatus;
import org.babyfish.jimmer.sql.DissociateAction;
import org.babyfish.jimmer.sql.Entity;
import org.babyfish.jimmer.sql.GeneratedValue;
import org.babyfish.jimmer.sql.GenerationType;
import org.babyfish.jimmer.sql.Id;
import org.babyfish.jimmer.sql.IdView;
import org.babyfish.jimmer.sql.ManyToOne;
import org.babyfish.jimmer.sql.OnDissociate;
import org.babyfish.jimmer.sql.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "orders")
@IntentService(value = "orders", uri = "/orders/{{id}}", maxTake = 50, defaultSort = { "created_at desc",
        "id desc" }, description = Descriptions.ORDERS)
public interface OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @IntentField
    long id();

    @ManyToOne
    @OnDissociate(DissociateAction.DELETE)
    CustomerEntity customer();

    @IdView("customer")
    @IntentField(description = Descriptions.ORDER_CUSTOMER_ID)
    long customerId();

    @IntentField
    OrderStatus status();

    @IntentField(description = Descriptions.ORDER_TOTAL)
    BigDecimal total();

    @IntentField(description = Descriptions.ORDER_ITEMS_COUNT)
    int itemsCount();

    @IntentField(description = Descriptions.ORDER_CREATED_AT)
    OffsetDateTime createdAt();
}
