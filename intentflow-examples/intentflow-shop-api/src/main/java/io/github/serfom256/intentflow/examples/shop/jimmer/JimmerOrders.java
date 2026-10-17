package io.github.serfom256.intentflow.examples.shop.jimmer;

import io.github.serfom256.intentflow.examples.shop.domain.Order;
import io.github.serfom256.intentflow.examples.shop.store.CrudStore;
import lombok.RequiredArgsConstructor;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.ast.Expression;
import org.babyfish.jimmer.sql.ast.mutation.SaveMode;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
final class JimmerOrders implements CrudStore<Order> {

    private final JSqlClient sql;

    @Override
    public List<Order> findPage(int page, int size) {
        OrderEntityTable t = OrderEntityTable.$;
        return sql.createQuery(t).orderBy(t.createdAt().desc(), t.id().desc()).select(t).fetchPage(page, size).getRows()
                .stream()
                .map(JimmerOrders::toDomain).toList();
    }

    @Override
    public long count() {
        return sql.createQuery(OrderEntityTable.$).select(Expression.rowCount()).fetchOne();
    }

    @Override
    public List<Order> findAll() {
        OrderEntityTable t = OrderEntityTable.$;
        return sql.createQuery(t).orderBy(t.id()).select(t).execute().stream().map(JimmerOrders::toDomain).toList();
    }

    @Override
    public Optional<Order> findById(long id) {
        return Optional.ofNullable(sql.findById(OrderEntity.class, id)).map(JimmerOrders::toDomain);
    }

    @Override
    public boolean exists(long id) {
        return sql.findById(OrderEntity.class, id) != null;
    }

    @Override
    public long insert(Order value) {
        return sql.saveCommand(entity(value)).setMode(SaveMode.INSERT_ONLY).execute().getModifiedEntity().id();
    }

    @Override
    public boolean update(Order value) {
        return sql.saveCommand(entity(value)).setMode(SaveMode.UPDATE_ONLY).execute().getTotalAffectedRowCount() > 0;
    }

    @Override
    public boolean delete(long id) {
        return sql.deleteById(OrderEntity.class, id).getAffectedRowCount(OrderEntity.class) > 0;
    }

    static Order toDomain(OrderEntity e) {
        return new Order(e.id(), e.customerId(), e.status(), e.total(), e.itemsCount(), e.createdAt());
    }

    private static OrderEntity entity(Order o) {
        return OrderEntityDraft.$.produce(d -> {
            if (o.getId() != null) {
                d.setId(o.getId());
            }
            d.setCustomerId(o.getCustomerId());
            d.setStatus(o.getStatus());
            d.setTotal(o.getTotal());
            d.setItemsCount(o.getItemsCount());
            d.setCreatedAt(o.getCreatedAt());
        });
    }
}
