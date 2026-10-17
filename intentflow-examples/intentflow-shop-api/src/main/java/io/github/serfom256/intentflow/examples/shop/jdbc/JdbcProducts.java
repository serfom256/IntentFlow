package io.github.serfom256.intentflow.examples.shop.jdbc;

import io.github.serfom256.intentflow.examples.shop.domain.Product;
import io.github.serfom256.intentflow.examples.shop.store.CrudStore;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class JdbcProducts implements CrudStore<Product> {

    private static final RowMapper<ProductRow> ROWS = DataClassRowMapper.newInstance(ProductRow.class);

    private final JdbcClient jdbc;

    JdbcProducts(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Product> findPage(int page, int size) {
        return jdbc.sql("SELECT * FROM product ORDER BY id LIMIT :size OFFSET :offset")
                .param("size", size).param("offset", (long) page * size)
                .query(ROWS).list().stream().map(ProductRow::toDomain).toList();
    }

    @Override
    public long count() {
        return jdbc.sql("SELECT count(*) FROM product").query(Long.class).single();
    }

    @Override
    public List<Product> findAll() {
        return jdbc.sql("SELECT * FROM product ORDER BY id").query(ROWS).list().stream().map(ProductRow::toDomain)
                .toList();
    }

    @Override
    public Optional<Product> findById(long id) {
        return jdbc.sql("SELECT * FROM product WHERE id = :id").param("id", id).query(ROWS).optional()
                .map(ProductRow::toDomain);
    }

    @Override
    public boolean exists(long id) {
        return jdbc.sql("SELECT count(*) FROM product WHERE id = :id").param("id", id).query(Long.class).single() > 0;
    }

    @Override
    public long insert(Product p) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.sql(
                "INSERT INTO product (name, brand, category, price, discount, rating, stock, active, supplier_cost, created_at) VALUES (:name, :brand, :category, :price, :discount, :rating, :stock, :active, :supplierCost, :createdAt)")
                .param("name", p.getName())
                .param("brand", p.getBrand())
                .param("category", p.getCategory().name())
                .param("price", p.getPrice())
                .param("discount", p.getDiscount())
                .param("rating", p.getRating())
                .param("stock", p.getStock())
                .param("active", p.isActive())
                .param("supplierCost", p.getSupplierCost())
                .param("createdAt", p.getCreatedAt())
                .update(key, "id");
        return Objects.requireNonNull(key.getKey()).longValue();
    }

    @Override
    public boolean update(Product p) {
        return jdbc.sql(
                "UPDATE product SET name = :name, brand = :brand, category = :category, price = :price, discount = :discount, rating = :rating, stock = :stock, active = :active, supplier_cost = :supplierCost WHERE id = :id")
                .param("id", p.getId())
                .param("name", p.getName())
                .param("brand", p.getBrand())
                .param("category", p.getCategory().name())
                .param("price", p.getPrice())
                .param("discount", p.getDiscount())
                .param("rating", p.getRating())
                .param("stock", p.getStock())
                .param("active", p.isActive())
                .param("supplierCost", p.getSupplierCost())
                .param("createdAt", p.getCreatedAt())
                .update() > 0;
    }

    @Override
    public boolean delete(long id) {
        return jdbc.sql("DELETE FROM product WHERE id = :id").param("id", id).update() > 0;
    }
}
