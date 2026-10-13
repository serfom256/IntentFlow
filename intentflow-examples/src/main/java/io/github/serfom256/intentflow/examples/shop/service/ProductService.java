package io.github.serfom256.intentflow.examples.shop.service;

import io.github.serfom256.intentflow.examples.shop.dao.ProductDao;
import io.github.serfom256.intentflow.examples.shop.domain.Product;
import io.github.serfom256.intentflow.examples.shop.web.ProductRequest;
import io.github.serfom256.model.page.PageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductDao products;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<Product> list(int page, int size) {
        return PageResponse.of(products.findPage(PageRequest.of(page, size)));
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
        if (products.delete(id) == 0) {
            throw new NotFoundException("Product", id);
        }
    }

    private static Product.ProductBuilder apply(Product.ProductBuilder b, ProductRequest r) {
        return b.name(r.name().strip())
                .brand(r.brand().strip())
                .category(r.category())
                .price(r.price())
                .rating(r.rating())
                .stock(r.stock())
                .active(r.active())
                .supplierCost(r.supplierCost());
    }
}
