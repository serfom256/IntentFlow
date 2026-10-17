package io.github.serfom256.intentflow.examples.shop.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    private @Nullable Long id;
    private String name;
    private String brand;
    private Category category;
    private BigDecimal price;
    private @Nullable BigDecimal discount;
    private BigDecimal rating;
    private int stock;
    private boolean active;
    private BigDecimal supplierCost;
    private OffsetDateTime createdAt;
}
