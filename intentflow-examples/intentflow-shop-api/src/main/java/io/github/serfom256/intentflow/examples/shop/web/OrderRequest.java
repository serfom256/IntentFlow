package io.github.serfom256.intentflow.examples.shop.web;

import io.github.serfom256.intentflow.examples.shop.domain.OrderStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record OrderRequest(@Positive long customerId, @NotNull OrderStatus status,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal total, @Positive int itemsCount) {
}
