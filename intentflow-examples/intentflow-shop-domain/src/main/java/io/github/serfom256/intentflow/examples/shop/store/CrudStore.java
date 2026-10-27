package io.github.serfom256.intentflow.examples.shop.store;

import java.util.List;
import java.util.Optional;

/**
 * Persistence of one domain type. OpenMapper implements it for products,
 * customers and orders under every profile; the {@code jpa}, {@code jimmer} and
 * {@code jdbc} profiles change only which entities search runs on, so the
 * services and the REST API stay the same.
 */
public interface CrudStore<T> {

    /** A page in the type's natural order: by id, newest first for orders. */
    List<T> findPage(int page, int size);

    long count();

    List<T> findAll();

    Optional<T> findById(long id);

    boolean exists(long id);

    /** Inserts and returns the generated id. */
    long insert(T value);

    /** @return false when no row has the value's id */
    boolean update(T value);

    /** @return false when no row has the id */
    boolean delete(long id);
}
