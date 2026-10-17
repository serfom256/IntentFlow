package io.github.serfom256.intentflow.examples.shop.jpa;

import io.github.serfom256.intentflow.examples.shop.domain.Customer;
import io.github.serfom256.intentflow.examples.shop.store.CrudStore;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@RequiredArgsConstructor
final class JpaCustomers implements CrudStore<Customer> {

    private static final Sort ORDER = Sort.by("id");

    private final CustomerRepository repository;

    @Override
    public List<Customer> findPage(int page, int size) {
        return repository.findAll(PageRequest.of(page, size, ORDER)).map(CustomerEntity::toDomain).getContent();
    }

    @Override
    public long count() {
        return repository.count();
    }

    @Override
    public List<Customer> findAll() {
        return repository.findAll(Sort.by("id")).stream().map(CustomerEntity::toDomain).toList();
    }

    @Override
    public Optional<Customer> findById(long id) {
        return repository.findById(id).map(CustomerEntity::toDomain);
    }

    @Override
    public boolean exists(long id) {
        return repository.existsById(id);
    }

    @Override
    public long insert(Customer value) {
        CustomerEntity entity = new CustomerEntity();
        entity.apply(value);
        return Objects.requireNonNull(repository.save(entity).getId());
    }

    @Override
    public boolean update(Customer value) {
        Optional<CustomerEntity> entity = repository.findById(Objects.requireNonNull(value.getId()));
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
