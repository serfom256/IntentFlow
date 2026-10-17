package io.github.serfom256.intentflow.examples.shop.service;

import io.github.serfom256.intentflow.examples.shop.domain.Product;
import io.github.serfom256.intentflow.examples.shop.store.CrudStore;
import io.github.serfom256.intentflow.examples.shop.web.ProductRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final CrudStore<Product> products;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<Product> list(int page, int size) {
        return PageResponse.of(products.findPage(page, size), page, size, products.count());
    }

    @Transactional(readOnly = true)
    public Product get(long id) {
        return products.findById(id).orElseThrow(() -> new NotFoundException("Product", id));
    }

    @Transactional
    public Product create(ProductRequest request) {
        Product product = apply(Product.builder(), request).createdAt(OffsetDateTime.now(clock)).build();
        return get(products.insert(product));
    }

    @Transactional
    public Product update(long id, ProductRequest request) {
        Product updated = apply(get(id).toBuilder(), request).build();
        products.update(updated);
        return get(id);
    }

    @Transactional
    public void delete(long id) {
        if (!products.delete(id)) {
            throw new NotFoundException("Product", id);
        }
    }

    private static Product.ProductBuilder apply(Product.ProductBuilder b, ProductRequest r) {
        return b.name(r.name().strip())
                .brand(r.brand().strip())
                .category(r.category())
                .price(r.price())
                .discount(r.discount())
                .rating(r.rating())
                .stock(r.stock())
                .active(r.active())
                .supplierCost(r.supplierCost());
    }
}
