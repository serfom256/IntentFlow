package io.github.serfom256.intentflow.examples.shop.openmapper;

import io.github.serfom256.annotations.DaoLayer;
import io.github.serfom256.annotations.DaoMethod;
import io.github.serfom256.annotations.Param;
import io.github.serfom256.model.operations.DmlOperation;
import io.github.serfom256.model.page.Page;
import io.github.serfom256.model.page.PageRequest;

import java.util.List;
import java.util.Optional;

/** Procedures live in {@code sql/order.fsql}. */
@DaoLayer
public interface OrderDao {

    @DaoMethod(procedure = "order.findById")
    Optional<OrderEntity> findById(@Param(name = "id") long id);

    @DaoMethod(procedure = "order.findAll")
    List<OrderEntity> findAll();

    @DaoMethod(procedure = "order.findPage", countProcedure = "order.count")
    Page<OrderEntity> findPage(PageRequest page);

    @DaoMethod(procedure = "order.count")
    long count();

    @DaoMethod(procedure = "order.exists")
    long countById(@Param(name = "id") long id);

    @DaoMethod(procedure = "order.insert", operation = DmlOperation.INSERT, returnKeys = true)
    long insert(OrderEntity order);

    @DaoMethod(procedure = "order.update", operation = DmlOperation.UPDATE)
    int update(OrderEntity order);

    @DaoMethod(procedure = "order.delete", operation = DmlOperation.DELETE)
    int delete(@Param(name = "id") long id);
}
