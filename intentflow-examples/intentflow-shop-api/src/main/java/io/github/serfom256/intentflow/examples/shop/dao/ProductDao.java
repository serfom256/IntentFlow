package io.github.serfom256.intentflow.examples.shop.dao;

import io.github.serfom256.annotations.DaoLayer;
import io.github.serfom256.annotations.DaoMethod;
import io.github.serfom256.annotations.Param;
import io.github.serfom256.intentflow.examples.shop.domain.Product;
import io.github.serfom256.model.operations.DmlOperation;
import io.github.serfom256.model.page.Page;
import io.github.serfom256.model.page.PageRequest;

import java.util.List;
import java.util.Optional;

/** Procedures live in {@code sql/product.fsql}. */
@DaoLayer
public interface ProductDao {

    @DaoMethod(procedure = "product.findById")
    Optional<Product> findById(@Param(name = "id") long id);

    @DaoMethod(procedure = "product.findAll")
    List<Product> findAll();

    @DaoMethod(procedure = "product.findPage", countProcedure = "product.count")
    Page<Product> findPage(PageRequest page);

    @DaoMethod(procedure = "product.exists")
    long countById(@Param(name = "id") long id);

    @DaoMethod(procedure = "product.insert", operation = DmlOperation.INSERT, returnKeys = true)
    long insert(Product product);

    @DaoMethod(procedure = "product.update", operation = DmlOperation.UPDATE)
    int update(Product product);

    @DaoMethod(procedure = "product.delete", operation = DmlOperation.DELETE)
    int delete(@Param(name = "id") long id);
}
