package io.github.serfom256.intentflow.examples.shop.dao;

import io.github.serfom256.annotations.DaoLayer;
import io.github.serfom256.annotations.DaoMethod;
import io.github.serfom256.annotations.Param;
import io.github.serfom256.intentflow.examples.shop.domain.Order;
import io.github.serfom256.model.operations.DmlOperation;
import io.github.serfom256.model.page.Page;
import io.github.serfom256.model.page.PageRequest;

import java.util.List;
import java.util.Optional;

/** Procedures live in {@code sql/order.fsql}. */
@DaoLayer
public interface OrderDao {

    @DaoMethod(procedure = "order.findById")
    Optional<Order> findById(@Param(name = "id") long id);

    @DaoMethod(procedure = "order.findAll")
    List<Order> findAll();

    @DaoMethod(procedure = "order.findPage", countProcedure = "order.count")
    Page<Order> findPage(PageRequest page);

    @DaoMethod(procedure = "order.exists")
    long countById(@Param(name = "id") long id);

    @DaoMethod(procedure = "order.insert", operation = DmlOperation.INSERT, returnKeys = true)
    long insert(Order order);

    @DaoMethod(procedure = "order.update", operation = DmlOperation.UPDATE)
    int update(Order order);

    @DaoMethod(procedure = "order.delete", operation = DmlOperation.DELETE)
    int delete(@Param(name = "id") long id);
}
