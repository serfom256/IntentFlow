package io.github.serfom256.intentflow.jpa;

import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class JpaQueryExecutor implements QueryExecutor {

    private final EntityManager entityManager;

    public JpaQueryExecutor(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
        if (resultType.getAnnotation(Entity.class) == null) {
            throw new IllegalArgumentException(resultType.getName() + " is not a JPA @Entity");
        }
        Query nativeQuery = entityManager.createNativeQuery(query.render(i -> "?" + i), resultType);
        List<Object> values = query.values();
        for (int i = 0; i < values.size(); i++) {
            nativeQuery.setParameter(i + 1, values.get(i));
        }
        List<?> rows = nativeQuery.getResultList();
        return rows.stream().map(resultType::cast).toList();
    }

    @Override
    public List<List<@Nullable Object>> rows(BoundQuery query) {
        Query nativeQuery = entityManager.createNativeQuery(query.render(i -> "?" + i));
        List<Object> values = query.values();
        for (int i = 0; i < values.size(); i++) {
            nativeQuery.setParameter(i + 1, values.get(i));
        }
        List<List<@Nullable Object>> rows = new ArrayList<>();
        for (Object row : nativeQuery.getResultList()) {
            rows.add(row instanceof Object[] columns ? Arrays.asList(columns) : Collections.singletonList(row));
        }
        return rows;
    }
}
