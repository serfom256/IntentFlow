package io.github.serfom256.intentflow.examples.shop.openmapper;

import io.github.serfom256.intentflow.examples.shop.domain.Customer;
import io.github.serfom256.intentflow.examples.shop.store.CrudStore;
import io.github.serfom256.model.page.PageRequest;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
final class OpenMapperCustomers implements CrudStore<Customer> {

    private final CustomerDao dao;

    @Override
    public List<Customer> findPage(int page, int size) {
        return dao.findPage(PageRequest.of(page, size)).content().stream().map(CustomerEntity::toDomain).toList();
    }

    @Override
    public long count() {
        return dao.count();
    }

    @Override
    public List<Customer> findAll() {
        return dao.findAll().stream().map(CustomerEntity::toDomain).toList();
    }

    @Override
    public Optional<Customer> findById(long id) {
        return dao.findById(id).map(CustomerEntity::toDomain);
    }

    @Override
    public boolean exists(long id) {
        return dao.countById(id) > 0;
    }

    @Override
    public long insert(Customer value) {
        return dao.insert(CustomerEntity.from(value));
    }

    @Override
    public boolean update(Customer value) {
        return dao.update(CustomerEntity.from(value)) > 0;
    }

    @Override
    public boolean delete(long id) {
        return dao.delete(id) > 0;
    }
}
