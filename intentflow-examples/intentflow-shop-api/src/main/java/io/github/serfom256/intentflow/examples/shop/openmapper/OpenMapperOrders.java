package io.github.serfom256.intentflow.examples.shop.openmapper;

import io.github.serfom256.intentflow.examples.shop.domain.Order;
import io.github.serfom256.intentflow.examples.shop.store.CrudStore;
import io.github.serfom256.model.page.PageRequest;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
final class OpenMapperOrders implements CrudStore<Order> {

    private final OrderDao dao;

    @Override
    public List<Order> findPage(int page, int size) {
        return dao.findPage(PageRequest.of(page, size)).content().stream().map(OrderEntity::toDomain).toList();
    }

    @Override
    public long count() {
        return dao.count();
    }

    @Override
    public List<Order> findAll() {
        return dao.findAll().stream().map(OrderEntity::toDomain).toList();
    }

    @Override
    public Optional<Order> findById(long id) {
        return dao.findById(id).map(OrderEntity::toDomain);
    }

    @Override
    public boolean exists(long id) {
        return dao.countById(id) > 0;
    }

    @Override
    public long insert(Order value) {
        return dao.insert(OrderEntity.from(value));
    }

    @Override
    public boolean update(Order value) {
        return dao.update(OrderEntity.from(value)) > 0;
    }

    @Override
    public boolean delete(long id) {
        return dao.delete(id) > 0;
    }
}
