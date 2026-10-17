package io.github.serfom256.intentflow.examples.shop.jimmer;

import io.github.serfom256.intentflow.examples.shop.domain.Product;
import io.github.serfom256.intentflow.examples.shop.store.CrudStore;
import lombok.RequiredArgsConstructor;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.ast.Expression;
import org.babyfish.jimmer.sql.ast.mutation.SaveMode;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
final class JimmerProducts implements CrudStore<Product> {

    private final JSqlClient sql;

    @Override
    public List<Product> findPage(int page, int size) {
        ProductEntityTable t = ProductEntityTable.$;
        return sql.createQuery(t).orderBy(t.id()).select(t).fetchPage(page, size).getRows().stream()
                .map(JimmerProducts::toDomain).toList();
    }

    @Override
    public long count() {
        return sql.createQuery(ProductEntityTable.$).select(Expression.rowCount()).fetchOne();
    }

    @Override
    public List<Product> findAll() {
        ProductEntityTable t = ProductEntityTable.$;
        return sql.createQuery(t).orderBy(t.id()).select(t).execute().stream().map(JimmerProducts::toDomain).toList();
    }

    @Override
    public Optional<Product> findById(long id) {
        return Optional.ofNullable(sql.findById(ProductEntity.class, id)).map(JimmerProducts::toDomain);
    }

    @Override
    public boolean exists(long id) {
        return sql.findById(ProductEntity.class, id) != null;
    }

    @Override
    public long insert(Product value) {
        return sql.saveCommand(entity(value)).setMode(SaveMode.INSERT_ONLY).execute().getModifiedEntity().id();
    }

    @Override
    public boolean update(Product value) {
        return sql.saveCommand(entity(value)).setMode(SaveMode.UPDATE_ONLY).execute().getTotalAffectedRowCount() > 0;
    }

    @Override
    public boolean delete(long id) {
        return sql.deleteById(ProductEntity.class, id).getAffectedRowCount(ProductEntity.class) > 0;
    }

    static Product toDomain(ProductEntity e) {
        return new Product(e.id(), e.name(), e.brand(), e.category(), e.price(), e.discount(), e.rating(), e.stock(),
                e.active(), e.supplierCost(), e.createdAt());
    }

    private static ProductEntity entity(Product p) {
        return ProductEntityDraft.$.produce(d -> {
            if (p.getId() != null) {
                d.setId(p.getId());
            }
            d.setName(p.getName());
            d.setBrand(p.getBrand());
            d.setCategory(p.getCategory());
            d.setPrice(p.getPrice());
            d.setDiscount(p.getDiscount());
            d.setRating(p.getRating());
            d.setStock(p.getStock());
            d.setActive(p.isActive());
            d.setSupplierCost(p.getSupplierCost());
            d.setCreatedAt(p.getCreatedAt());
        });
    }
}
