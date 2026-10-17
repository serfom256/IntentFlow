package io.github.serfom256.intentflow.examples.shop.jpa;

import io.github.serfom256.intentflow.examples.shop.domain.Order;
import io.github.serfom256.intentflow.examples.shop.store.CrudStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@RequiredArgsConstructor
final class JpaOrders implements CrudStore<Order> {

    private static final Sort ORDER = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    private final OrderRepository repository;
    private final CustomerRepository customers;

    @Override
    public List<Order> findPage(int page, int size) {
        return repository.findAll(PageRequest.of(page, size, ORDER)).map(OrderEntity::toDomain).getContent();
    }

    @Override
    public long count() {
        return repository.count();
    }

    @Override
    public List<Order> findAll() {
        return repository.findAll(Sort.by("id")).stream().map(OrderEntity::toDomain).toList();
    }

    @Override
    public Optional<Order> findById(long id) {
        return repository.findById(id).map(OrderEntity::toDomain);
    }

    @Override
    public boolean exists(long id) {
        return repository.existsById(id);
    }

    @Override
    public long insert(Order value) {
        OrderEntity entity = new OrderEntity();
        entity.apply(value, customers.getReferenceById(value.getCustomerId()));
        return Objects.requireNonNull(repository.save(entity).getId());
    }

    @Override
    public boolean update(Order value) {
        Optional<OrderEntity> entity = repository.findById(Objects.requireNonNull(value.getId()));
        entity.ifPresent(e -> e.apply(value, customers.getReferenceById(value.getCustomerId())));
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
