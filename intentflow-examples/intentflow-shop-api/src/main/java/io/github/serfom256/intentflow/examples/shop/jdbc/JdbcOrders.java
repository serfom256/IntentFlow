package io.github.serfom256.intentflow.examples.shop.jdbc;

import io.github.serfom256.intentflow.examples.shop.domain.Order;
import io.github.serfom256.intentflow.examples.shop.store.CrudStore;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class JdbcOrders implements CrudStore<Order> {

    private static final RowMapper<OrderRow> ROWS = DataClassRowMapper.newInstance(OrderRow.class);

    private final JdbcClient jdbc;

    JdbcOrders(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Order> findPage(int page, int size) {
        return jdbc.sql("SELECT * FROM orders ORDER BY created_at DESC, id DESC LIMIT :size OFFSET :offset")
                .param("size", size).param("offset", (long) page * size)
                .query(ROWS).list().stream().map(OrderRow::toDomain).toList();
    }

    @Override
    public long count() {
        return jdbc.sql("SELECT count(*) FROM orders").query(Long.class).single();
    }

    @Override
    public List<Order> findAll() {
        return jdbc.sql("SELECT * FROM orders ORDER BY id").query(ROWS).list().stream().map(OrderRow::toDomain)
                .toList();
    }

    @Override
    public Optional<Order> findById(long id) {
        return jdbc.sql("SELECT * FROM orders WHERE id = :id").param("id", id).query(ROWS).optional()
                .map(OrderRow::toDomain);
    }

    @Override
    public boolean exists(long id) {
        return jdbc.sql("SELECT count(*) FROM orders WHERE id = :id").param("id", id).query(Long.class).single() > 0;
    }

    @Override
    public long insert(Order o) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.sql(
                "INSERT INTO orders (customer_id, status, total, items_count, created_at) VALUES (:customerId, :status, :total, :itemsCount, :createdAt)")
                .param("customerId", o.getCustomerId())
                .param("status", o.getStatus().name())
                .param("total", o.getTotal())
                .param("itemsCount", o.getItemsCount())
                .param("createdAt", o.getCreatedAt())
                .update(key, "id");
        return Objects.requireNonNull(key.getKey()).longValue();
    }

    @Override
    public boolean update(Order o) {
        return jdbc.sql(
                "UPDATE orders SET customer_id = :customerId, status = :status, total = :total, items_count = :itemsCount WHERE id = :id")
                .param("id", o.getId())
                .param("customerId", o.getCustomerId())
                .param("status", o.getStatus().name())
                .param("total", o.getTotal())
                .param("itemsCount", o.getItemsCount())
                .param("createdAt", o.getCreatedAt())
                .update() > 0;
    }

    @Override
    public boolean delete(long id) {
        return jdbc.sql("DELETE FROM orders WHERE id = :id").param("id", id).update() > 0;
    }
}
