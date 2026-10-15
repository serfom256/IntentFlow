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

import java.time.OffsetDateTime;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Model(primaryKey = "id")
@IntentService(value = "customers", table = "customer", uri = "/customers/{{id}}", maxTake = 50,
        defaultSort = "id", description = "Registered shop customers")
@IntentRelation(name = "orders", service = "orders", from = "id", to = "customer_id")
public class Customer {

    @Field
    @IntentField
    private @Nullable Long id;

    @Field(name = "full_name")
    @IntentField(name = "name")
    private String fullName;

    @Field
    private String email;

    @Field
    @IntentField
    private String city;

    @Field
    @IntentField(description = "ISO 3166 two-letter code, e.g. US, DE, FR")
    private String country;

    @Field
    @IntentField(description = "member of the loyalty program")
    private boolean vip;

    @Field(name = "created_at")
    @IntentField(description = "when the customer signed up")
    private OffsetDateTime createdAt;
}
