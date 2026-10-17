package io.github.serfom256.intentflow.examples.shop.openmapper;

import io.github.serfom256.intentflow.examples.shop.domain.Product;
import io.github.serfom256.intentflow.examples.shop.store.CrudStore;
import io.github.serfom256.model.page.PageRequest;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
final class OpenMapperProducts implements CrudStore<Product> {

    private final ProductDao dao;

    @Override
    public List<Product> findPage(int page, int size) {
        return dao.findPage(PageRequest.of(page, size)).content().stream().map(ProductEntity::toDomain).toList();
    }

    @Override
    public long count() {
        return dao.count();
    }

    @Override
    public List<Product> findAll() {
        return dao.findAll().stream().map(ProductEntity::toDomain).toList();
    }

    @Override
    public Optional<Product> findById(long id) {
        return dao.findById(id).map(ProductEntity::toDomain);
    }

    @Override
    public boolean exists(long id) {
        return dao.countById(id) > 0;
    }

    @Override
    public long insert(Product value) {
        return dao.insert(ProductEntity.from(value));
    }

    @Override
    public boolean update(Product value) {
        return dao.update(ProductEntity.from(value)) > 0;
    }

    @Override
    public boolean delete(long id) {
        return dao.delete(id) > 0;
    }
}
