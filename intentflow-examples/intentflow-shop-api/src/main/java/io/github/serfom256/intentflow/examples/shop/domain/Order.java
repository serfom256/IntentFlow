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
public class Order {

    private @Nullable Long id;
    private long customerId;
    private OrderStatus status;
    private BigDecimal total;
    private int itemsCount;
    private OffsetDateTime createdAt;
}
