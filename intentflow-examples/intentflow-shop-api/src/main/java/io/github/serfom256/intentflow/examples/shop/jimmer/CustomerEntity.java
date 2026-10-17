package io.github.serfom256.intentflow.examples.shop.jimmer;

import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.examples.shop.domain.Descriptions;
import org.babyfish.jimmer.sql.Entity;
import org.babyfish.jimmer.sql.GeneratedValue;
import org.babyfish.jimmer.sql.GenerationType;
import org.babyfish.jimmer.sql.Id;
import org.babyfish.jimmer.sql.OneToMany;
import org.babyfish.jimmer.sql.Table;

import java.time.OffsetDateTime;
import java.util.List;

@Entity
@Table(name = "customer")
@IntentService(value = "customers", uri = "/customers/{{id}}", maxTake = 50, defaultSort = "id", description = Descriptions.CUSTOMERS)
public interface CustomerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @IntentField
    long id();

    @IntentField(name = "name")
    String fullName();

    String email();

    @IntentField(description = Descriptions.CUSTOMER_CITY)
    String city();

    @IntentField(description = Descriptions.CUSTOMER_COUNTRY)
    String country();

    @IntentField(description = Descriptions.CUSTOMER_VIP)
    boolean vip();

    @IntentField(description = Descriptions.CUSTOMER_CREATED_AT)
    OffsetDateTime createdAt();

    @OneToMany(mappedBy = "customer")
    List<OrderEntity> orders();
}
