package io.github.serfom256.intentflow.examples.shop.jdbc;

import io.github.serfom256.intentflow.examples.shop.domain.Customer;
import io.github.serfom256.intentflow.examples.shop.store.CrudStore;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class JdbcCustomers implements CrudStore<Customer> {

    private static final RowMapper<CustomerRow> ROWS = DataClassRowMapper.newInstance(CustomerRow.class);

    private final JdbcClient jdbc;

    JdbcCustomers(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Customer> findPage(int page, int size) {
        return jdbc.sql("SELECT * FROM customer ORDER BY id LIMIT :size OFFSET :offset")
                .param("size", size).param("offset", (long) page * size)
                .query(ROWS).list().stream().map(CustomerRow::toDomain).toList();
    }

    @Override
    public long count() {
        return jdbc.sql("SELECT count(*) FROM customer").query(Long.class).single();
    }

    @Override
    public List<Customer> findAll() {
        return jdbc.sql("SELECT * FROM customer ORDER BY id").query(ROWS).list().stream().map(CustomerRow::toDomain)
                .toList();
    }

    @Override
    public Optional<Customer> findById(long id) {
        return jdbc.sql("SELECT * FROM customer WHERE id = :id").param("id", id).query(ROWS).optional()
                .map(CustomerRow::toDomain);
    }

    @Override
    public boolean exists(long id) {
        return jdbc.sql("SELECT count(*) FROM customer WHERE id = :id").param("id", id).query(Long.class).single() > 0;
    }

    @Override
    public long insert(Customer c) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.sql(
                "INSERT INTO customer (full_name, email, city, country, vip, created_at) VALUES (:fullName, :email, :city, :country, :vip, :createdAt)")
                .param("fullName", c.getFullName())
                .param("email", c.getEmail())
                .param("city", c.getCity())
                .param("country", c.getCountry())
                .param("vip", c.isVip())
                .param("createdAt", c.getCreatedAt())
                .update(key, "id");
        return Objects.requireNonNull(key.getKey()).longValue();
    }

    @Override
    public boolean update(Customer c) {
        return jdbc.sql(
                "UPDATE customer SET full_name = :fullName, email = :email, city = :city, country = :country, vip = :vip WHERE id = :id")
                .param("id", c.getId())
                .param("fullName", c.getFullName())
                .param("email", c.getEmail())
                .param("city", c.getCity())
                .param("country", c.getCountry())
                .param("vip", c.isVip())
                .param("createdAt", c.getCreatedAt())
                .update() > 0;
    }

    @Override
    public boolean delete(long id) {
        return jdbc.sql("DELETE FROM customer WHERE id = :id").param("id", id).update() > 0;
    }
}
