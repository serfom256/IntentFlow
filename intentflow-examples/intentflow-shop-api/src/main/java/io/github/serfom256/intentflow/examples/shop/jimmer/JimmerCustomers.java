package io.github.serfom256.intentflow.examples.shop.jimmer;

import io.github.serfom256.intentflow.examples.shop.domain.Customer;
import io.github.serfom256.intentflow.examples.shop.store.CrudStore;
import lombok.RequiredArgsConstructor;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.ast.Expression;
import org.babyfish.jimmer.sql.ast.mutation.SaveMode;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
final class JimmerCustomers implements CrudStore<Customer> {

    private final JSqlClient sql;

    @Override
    public List<Customer> findPage(int page, int size) {
        CustomerEntityTable t = CustomerEntityTable.$;
        return sql.createQuery(t).orderBy(t.id()).select(t).fetchPage(page, size).getRows().stream()
                .map(JimmerCustomers::toDomain).toList();
    }

    @Override
    public long count() {
        return sql.createQuery(CustomerEntityTable.$).select(Expression.rowCount()).fetchOne();
    }

    @Override
    public List<Customer> findAll() {
        CustomerEntityTable t = CustomerEntityTable.$;
        return sql.createQuery(t).orderBy(t.id()).select(t).execute().stream().map(JimmerCustomers::toDomain).toList();
    }

    @Override
    public Optional<Customer> findById(long id) {
        return Optional.ofNullable(sql.findById(CustomerEntity.class, id)).map(JimmerCustomers::toDomain);
    }

    @Override
    public boolean exists(long id) {
        return sql.findById(CustomerEntity.class, id) != null;
    }

    @Override
    public long insert(Customer value) {
        return sql.saveCommand(entity(value)).setMode(SaveMode.INSERT_ONLY).execute().getModifiedEntity().id();
    }

    @Override
    public boolean update(Customer value) {
        return sql.saveCommand(entity(value)).setMode(SaveMode.UPDATE_ONLY).execute().getTotalAffectedRowCount() > 0;
    }

    @Override
    public boolean delete(long id) {
        return sql.deleteById(CustomerEntity.class, id).getAffectedRowCount(CustomerEntity.class) > 0;
    }

    static Customer toDomain(CustomerEntity e) {
        return new Customer(e.id(), e.fullName(), e.email(), e.city(), e.country(), e.vip(), e.createdAt());
    }

    private static CustomerEntity entity(Customer c) {
        return CustomerEntityDraft.$.produce(d -> {
            if (c.getId() != null) {
                d.setId(c.getId());
            }
            d.setFullName(c.getFullName());
            d.setEmail(c.getEmail());
            d.setCity(c.getCity());
            d.setCountry(c.getCountry());
            d.setVip(c.isVip());
            d.setCreatedAt(c.getCreatedAt());
        });
    }
}
