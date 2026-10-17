package io.github.serfom256.intentflow.examples.shop.openmapper;

import io.github.serfom256.annotations.DaoLayer;
import io.github.serfom256.annotations.DaoMethod;
import io.github.serfom256.annotations.Param;
import io.github.serfom256.model.operations.DmlOperation;
import io.github.serfom256.model.page.Page;
import io.github.serfom256.model.page.PageRequest;

import java.util.List;
import java.util.Optional;

/** Procedures live in {@code sql/customer.fsql}. */
@DaoLayer
public interface CustomerDao {

    @DaoMethod(procedure = "customer.findById")
    Optional<CustomerEntity> findById(@Param(name = "id") long id);

    @DaoMethod(procedure = "customer.findAll")
    List<CustomerEntity> findAll();

    @DaoMethod(procedure = "customer.findPage", countProcedure = "customer.count")
    Page<CustomerEntity> findPage(PageRequest page);

    @DaoMethod(procedure = "customer.count")
    long count();

    @DaoMethod(procedure = "customer.exists")
    long countById(@Param(name = "id") long id);

    @DaoMethod(procedure = "customer.insert", operation = DmlOperation.INSERT, returnKeys = true)
    long insert(CustomerEntity customer);

    @DaoMethod(procedure = "customer.update", operation = DmlOperation.UPDATE)
    int update(CustomerEntity customer);

    @DaoMethod(procedure = "customer.delete", operation = DmlOperation.DELETE)
    int delete(@Param(name = "id") long id);
}
