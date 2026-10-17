package io.github.serfom256.intentflow.examples.shop.jdbc;

import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentRelation;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.examples.shop.domain.Customer;
import io.github.serfom256.intentflow.examples.shop.domain.Descriptions;

import java.time.OffsetDateTime;

@IntentService(value = "customers", table = "customer", uri = "/customers/{{id}}", maxTake = 50, defaultSort = "id", description = Descriptions.CUSTOMERS)
@IntentRelation(name = "orders", service = "orders", from = "id", to = "customer_id")
public record CustomerRow(
        @IntentField long id,
        @IntentField(name = "name") String fullName,
        String email,
        @IntentField(description = Descriptions.CUSTOMER_CITY) String city,
        @IntentField(description = Descriptions.CUSTOMER_COUNTRY) String country,
        @IntentField(description = Descriptions.CUSTOMER_VIP) boolean vip,
        @IntentField(description = Descriptions.CUSTOMER_CREATED_AT) OffsetDateTime createdAt) {

    Customer toDomain() {
        return new Customer(id, fullName, email, city, country, vip, createdAt);
    }
}
