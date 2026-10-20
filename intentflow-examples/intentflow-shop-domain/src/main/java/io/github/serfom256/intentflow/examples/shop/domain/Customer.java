package io.github.serfom256.intentflow.examples.shop.domain;

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
public class Customer {

    private @Nullable Long id;
    private String fullName;
    private String email;
    private String city;
    private String country;
    private boolean vip;
    private OffsetDateTime createdAt;
}
