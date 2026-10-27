package io.github.serfom256.intentflow.examples.shop.search;

import io.github.serfom256.intentflow.examples.shop.domain.Category;
import io.github.serfom256.intentflow.examples.shop.domain.Product;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ProductView(@Nullable Long id, String name, String brand, Category category, BigDecimal price,
        @Nullable BigDecimal discount, BigDecimal rating, int stock, boolean active, OffsetDateTime createdAt) {

    static ProductView of(Product p) {
        return new ProductView(p.getId(), p.getName(), p.getBrand(), p.getCategory(), p.getPrice(), p.getDiscount(),
                p.getRating(), p.getStock(), p.isActive(), p.getCreatedAt());
    }
}
