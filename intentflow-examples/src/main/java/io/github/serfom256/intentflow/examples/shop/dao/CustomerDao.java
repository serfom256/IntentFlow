package io.github.serfom256.intentflow.examples.shop.dao;

import io.github.serfom256.annotations.DaoLayer;
import io.github.serfom256.annotations.DaoMethod;
import io.github.serfom256.annotations.Param;
import io.github.serfom256.intentflow.examples.shop.domain.Customer;
import io.github.serfom256.model.operations.DmlOperation;
import io.github.serfom256.model.page.Page;
import io.github.serfom256.model.page.PageRequest;

import java.util.List;
import java.util.Optional;

/** Procedures live in {@code sql/customer.fsql}. */
@DaoLayer
public interface CustomerDao {

    @DaoMethod(procedure = "customer.findById")
    Optional<Customer> findById(@Param(name = "id") long id);

    @DaoMethod(procedure = "customer.findAll")
    List<Customer> findAll();

    @DaoMethod(procedure = "customer.findPage", countProcedure = "customer.count")
    Page<Customer> findPage(PageRequest page);

    @DaoMethod(procedure = "customer.exists")
    long countById(@Param(name = "id") long id);

    @DaoMethod(procedure = "customer.insert", operation = DmlOperation.INSERT, returnKeys = true)
    long insert(Customer customer);

    @DaoMethod(procedure = "customer.update", operation = DmlOperation.UPDATE)
    int update(Customer customer);

    @DaoMethod(procedure = "customer.delete", operation = DmlOperation.DELETE)
    int delete(@Param(name = "id") long id);
}
