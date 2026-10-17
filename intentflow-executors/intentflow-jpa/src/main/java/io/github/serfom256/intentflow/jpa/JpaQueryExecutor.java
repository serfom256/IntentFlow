package io.github.serfom256.intentflow.jpa;

import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.catalog.factories.ColumnNames;
import io.github.serfom256.intentflow.core.pipeline.QueryExecutor;
import io.github.serfom256.intentflow.core.pipeline.ResultShape;
import io.github.serfom256.intentflow.core.pipeline.RuntimeRestriction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Id;
import jakarta.persistence.PersistenceUnitUtil;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.metamodel.BasicType;
import jakarta.persistence.metamodel.EntityType;
import org.hibernate.Session;
import org.hibernate.graph.GraphSemantic;
import org.hibernate.query.SelectionQuery;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class JpaQueryExecutor implements QueryExecutor {

    private static final boolean HIBERNATE = present("org.hibernate.Session");

    private final EntityManager entityManager;

    public JpaQueryExecutor(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public <T> List<T> execute(BoundQuery query, Class<T> resultType) {
        if (resultType.getAnnotation(Entity.class) == null) {
            throw new IllegalArgumentException(resultType.getName() + " is not a JPA @Entity");
        }
        return loadsById(resultType) ? loadById(query, resultType) : mapNative(query, resultType);
    }

    @Override
    public ResultShape shape(Class<?> resultType) {
        return loadsById(resultType) ? ResultShape.KEYS : ResultShape.ROWS;
    }

    @Override
    public List<RuntimeRestriction> restrictions(Class<?> entityType) {
        if (!HIBERNATE || entityType.getAnnotation(Entity.class) == null) {
            return List.of();
        }
        return HibernateFilters.enabled(entityManager.unwrap(Session.class), entityType);
    }

    private boolean loadsById(Class<?> resultType) {
        if (!HIBERNATE || resultType.getAnnotation(Entity.class) == null) {
            return false;
        }
        EntityType<?> entity = entityManager.getMetamodel().entity(resultType);
        return entity.hasSingleIdAttribute() && entity.getIdType() instanceof BasicType<?>;
    }

    private <T> List<T> loadById(BoundQuery query, Class<T> resultType) {
        EntityType<T> entity = entityManager.getMetamodel().entity(resultType);
        String idAttribute = entity.getId(entity.getIdType().getJavaType()).getName();
        Class<?> idType = boxed(entity.getIdType().getJavaType());
        String idColumn = idColumn(resultType, idAttribute);

        List<Object> ids = entityManager.unwrap(Session.class).doReturningWork(connection -> {
            List<Object> out = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(query.sql())) {
                List<@Nullable Object> values = query.values();
                for (int i = 0; i < values.size(); i++) {
                    statement.setObject(i + 1, values.get(i));
                }
                try (ResultSet rs = statement.executeQuery()) {
                    int column = rs.findColumn(idColumn);
                    while (rs.next()) {
                        out.add(rs.getObject(column, idType));
                    }
                }
            }
            return out;
        });
        if (ids.isEmpty()) {
            return List.of();
        }

        TypedQuery<T> load = entityManager.createQuery(
                "select e from " + entity.getName() + " e where e." + idAttribute + " in :ids", resultType);
        load.setParameter("ids", ids);
        entityManager.getEntityGraphs(resultType).stream()
                .filter(g -> g.getName() != null
                        && (g.getName().equals("intentflow") || g.getName().endsWith(".intentflow")))
                .findFirst()
                .ifPresent(fetch -> load.unwrap(SelectionQuery.class).setEntityGraph(fetch, GraphSemantic.FETCH));
        Map<Object, T> byId = new HashMap<>();
        PersistenceUnitUtil util = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();
        for (T row : load.getResultList()) {
            byId.put(util.getIdentifier(row), row);
        }
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    private <T> List<T> mapNative(BoundQuery query, Class<T> resultType) {
        Query nativeQuery = entityManager.createNativeQuery(query.render(i -> "?" + i), resultType);
        List<@Nullable Object> values = query.values();
        for (int i = 0; i < values.size(); i++) {
            nativeQuery.setParameter(i + 1, values.get(i));
        }
        List<?> rows = nativeQuery.getResultList();
        return rows.stream().map(resultType::cast).toList();
    }

    @Override
    public List<List<@Nullable Object>> rows(BoundQuery query) {
        Query nativeQuery = entityManager.createNativeQuery(query.render(i -> "?" + i));
        List<@Nullable Object> values = query.values();
        for (int i = 0; i < values.size(); i++) {
            nativeQuery.setParameter(i + 1, values.get(i));
        }
        List<List<@Nullable Object>> rows = new ArrayList<>();
        for (Object row : nativeQuery.getResultList()) {
            rows.add(row instanceof Object[] columns ? Arrays.asList(columns) : Collections.singletonList(row));
        }
        return rows;
    }

    private static String idColumn(Class<?> type, String attribute) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (field.getName().equals(attribute) && field.isAnnotationPresent(Id.class)) {
                    Column column = field.getAnnotation(Column.class);
                    return column != null && !column.name().isEmpty() ? column.name()
                            : ColumnNames.snakeCase(attribute);
                }
            }
        }
        return ColumnNames.snakeCase(attribute);
    }

    private static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive())
            return type;
        if (type == long.class)
            return Long.class;
        if (type == int.class)
            return Integer.class;
        if (type == short.class)
            return Short.class;
        if (type == byte.class)
            return Byte.class;
        return type;
    }

    private static boolean present(String className) {
        try {
            Class.forName(className, false, JpaQueryExecutor.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
