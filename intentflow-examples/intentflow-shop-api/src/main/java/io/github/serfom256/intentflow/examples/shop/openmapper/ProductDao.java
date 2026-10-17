package io.github.serfom256.intentflow.examples.shop.openmapper;

import io.github.serfom256.annotations.DaoLayer;
import io.github.serfom256.annotations.DaoMethod;
import io.github.serfom256.annotations.Param;
import io.github.serfom256.model.operations.DmlOperation;
import io.github.serfom256.model.page.Page;
import io.github.serfom256.model.page.PageRequest;

import java.util.List;
import java.util.Optional;

/** Procedures live in {@code sql/product.fsql}. */
@DaoLayer
public interface ProductDao {

    @DaoMethod(procedure = "product.findById")
    Optional<ProductEntity> findById(@Param(name = "id") long id);

    @DaoMethod(procedure = "product.findAll")
    List<ProductEntity> findAll();

    @DaoMethod(procedure = "product.findPage", countProcedure = "product.count")
    Page<ProductEntity> findPage(PageRequest page);

    @DaoMethod(procedure = "product.count")
    long count();

    @DaoMethod(procedure = "product.exists")
    long countById(@Param(name = "id") long id);

    @DaoMethod(procedure = "product.insert", operation = DmlOperation.INSERT, returnKeys = true)
    long insert(ProductEntity product);

    @DaoMethod(procedure = "product.update", operation = DmlOperation.UPDATE)
    int update(ProductEntity product);

    @DaoMethod(procedure = "product.delete", operation = DmlOperation.DELETE)
    int delete(@Param(name = "id") long id);
}
