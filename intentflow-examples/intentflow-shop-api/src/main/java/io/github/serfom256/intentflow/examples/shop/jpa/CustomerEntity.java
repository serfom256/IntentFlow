package io.github.serfom256.intentflow.examples.shop.jpa;

import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.examples.shop.domain.Customer;
import io.github.serfom256.intentflow.examples.shop.domain.Descriptions;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customer")
@IntentService(value = "customers", uri = "/customers/{{id}}", maxTake = 50, defaultSort = "id", description = Descriptions.CUSTOMERS)
@Getter
@Setter
public class CustomerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @IntentField
    private @Nullable Long id;

    @IntentField(name = "name")
    private String fullName;

    private String email;

    @IntentField(description = Descriptions.CUSTOMER_CITY)
    private String city;

    @IntentField(description = Descriptions.CUSTOMER_COUNTRY)
    private String country;

    @IntentField(description = Descriptions.CUSTOMER_VIP)
    private boolean vip;

    @IntentField(description = Descriptions.CUSTOMER_CREATED_AT)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "customer")
    private List<OrderEntity> orders = new ArrayList<>();

    void apply(Customer c) {
        fullName = c.getFullName();
        email = c.getEmail();
        city = c.getCity();
        country = c.getCountry();
        vip = c.isVip();
        createdAt = c.getCreatedAt();
    }

    Customer toDomain() {
        return new Customer(id, fullName, email, city, country, vip, createdAt);
    }
}
