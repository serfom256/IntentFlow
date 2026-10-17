package io.github.serfom256.intentflow.examples.shop.jpa;

import io.github.serfom256.intentflow.examples.shop.domain.Product;
import io.github.serfom256.intentflow.examples.shop.store.CrudStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@RequiredArgsConstructor
final class JpaProducts implements CrudStore<Product> {

    private static final Sort ORDER = Sort.by("id");

    private final ProductRepository repository;

    @Override
    public List<Product> findPage(int page, int size) {
        return repository.findAll(PageRequest.of(page, size, ORDER)).map(ProductEntity::toDomain).getContent();
    }

    @Override
    public long count() {
        return repository.count();
    }

    @Override
    public List<Product> findAll() {
        return repository.findAll(Sort.by("id")).stream().map(ProductEntity::toDomain).toList();
    }

    @Override
    public Optional<Product> findById(long id) {
        return repository.findById(id).map(ProductEntity::toDomain);
    }

    @Override
    public boolean exists(long id) {
        return repository.existsById(id);
    }

    @Override
    public long insert(Product value) {
        ProductEntity entity = new ProductEntity();
        entity.apply(value);
        return Objects.requireNonNull(repository.save(entity).getId());
    }

    @Override
    public boolean update(Product value) {
        Optional<ProductEntity> entity = repository.findById(Objects.requireNonNull(value.getId()));
        entity.ifPresent(e -> e.apply(value));
        return entity.isPresent();
    }

    @Override
    public boolean delete(long id) {
        if (!repository.existsById(id)) {
            return false;
        }
        repository.deleteById(id);
        return true;
    }
}
