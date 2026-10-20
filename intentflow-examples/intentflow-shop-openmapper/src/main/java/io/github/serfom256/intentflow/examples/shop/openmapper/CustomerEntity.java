package io.github.serfom256.intentflow.examples.shop.openmapper;

import io.github.serfom256.annotations.entity.Field;
import io.github.serfom256.annotations.entity.Model;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentRelation;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.examples.shop.domain.Customer;
import io.github.serfom256.intentflow.examples.shop.domain.Descriptions;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Model(primaryKey = "id")
@IntentService(value = "customers", table = "customer", uri = "/customers/{{id}}", maxTake = 50, defaultSort = "id", description = Descriptions.CUSTOMERS)
@IntentRelation(name = "orders", service = "orders", from = "id", to = "customer_id")
public class CustomerEntity {

    @Field
    @IntentField
    private @Nullable Long id;

    @Field(name = "full_name")
    @IntentField(name = "name")
    private String fullName;

    @Field
    private String email;

    @Field
    @IntentField(description = Descriptions.CUSTOMER_CITY)
    private String city;

    @Field
    @IntentField(description = Descriptions.CUSTOMER_COUNTRY)
    private String country;

    @Field
    @IntentField(description = Descriptions.CUSTOMER_VIP)
    private boolean vip;

    @Field(name = "created_at")
    @IntentField(description = Descriptions.CUSTOMER_CREATED_AT)
    private OffsetDateTime createdAt;

    static CustomerEntity from(Customer c) {
        return new CustomerEntity(c.getId(), c.getFullName(), c.getEmail(), c.getCity(), c.getCountry(), c.isVip(),
                c.getCreatedAt());
    }

    Customer toDomain() {
        return new Customer(id, fullName, email, city, country, vip, createdAt);
    }
}
