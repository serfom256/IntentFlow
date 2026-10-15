package io.github.serfom256.intentflow.examples.shop.web;

import io.github.serfom256.intentflow.examples.shop.domain.Category;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 60) String brand,
        @NotNull Category category,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
        @NotNull @DecimalMin("0.0") @DecimalMax("5.0") @Digits(integer = 1, fraction = 1) BigDecimal rating,
        @PositiveOrZero int stock,
        boolean active,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal supplierCost) {
}
