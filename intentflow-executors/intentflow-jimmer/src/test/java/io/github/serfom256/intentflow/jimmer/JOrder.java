package io.github.serfom256.intentflow.jimmer;

import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import org.babyfish.jimmer.sql.Entity;
import org.babyfish.jimmer.sql.Id;
import org.babyfish.jimmer.sql.ManyToOne;
import org.babyfish.jimmer.sql.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "rel_order")
@IntentService(value = "orders", uri = "/orders/{{id}}", defaultSort = "id")
public interface JOrder {

    @Id
    @IntentField
    long id();

    @ManyToOne
    JCustomer customer();

    @IntentField
    String status();

    @IntentField
    BigDecimal total();
}
