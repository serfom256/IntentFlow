package io.github.serfom256.intentflow.openmapper;

import io.github.serfom256.annotations.entity.Joined;
import io.github.serfom256.intentflow.core.ast.BoundQuery;
import io.github.serfom256.intentflow.core.pipeline.RelatedRows;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

final class JoinedLoader {

    private final RelatedRows related;
    private final BiFunction<BoundQuery, Class<?>, List<?>> select;

    JoinedLoader(RelatedRows related, BiFunction<BoundQuery, Class<?>, List<?>> select) {
        this.related = related;
        this.select = select;
    }

    void load(Class<?> type, List<?> parents) {
        if (parents.isEmpty()) {
            return;
        }
        for (Field field : fields(type)) {
            Joined joined = field.getAnnotation(Joined.class);
            Class<?> target = joined == null ? null : target(field);
            if (target != null) {
                load(type, parents, field, joined, target);
            }
        }
    }

    private void load(Class<?> type, List<?> parents, Field field, Joined joined, Class<?> target) {
        Field joinBy = field(type, joined.joinBy());
        Field to = field(target, joined.to());
        if (joinBy == null || to == null) {
            return;
        }
        Set<Object> keys = new LinkedHashSet<>();
        parents.forEach(parent -> {
            Object key = key(get(joinBy, parent));
            if (key != null) {
                keys.add(key);
            }
        });
        Optional<List<BoundQuery>> queries = keys.isEmpty() ? Optional.of(List.of())
                : related.queries(type, field.getName(), keys);
        if (queries.isEmpty()) {
            return;
        }
        Map<Object, List<Object>> children = new LinkedHashMap<>();
        for (BoundQuery query : queries.get()) {
            for (Object child : select.apply(query, target)) {
                children.computeIfAbsent(key(get(to, child)), k -> new ArrayList<>()).add(child);
            }
        }
        linkBack(type, parents, target, children.values().stream().flatMap(List::stream).toList());
        for (Object parent : parents) {
            List<Object> matches = children.getOrDefault(key(get(joinBy, parent)), List.of());
            set(field, parent, collection(field, matches));
        }
    }

    private static void linkBack(Class<?> type, List<?> parents, Class<?> target, List<Object> children) {
        for (Field back : fields(target)) {
            Joined joined = back.getAnnotation(Joined.class);
            if (joined == null || back.getType() != type) {
                continue;
            }
            Field from = field(target, joined.joinBy());
            Field to = field(type, joined.to());
            if (from == null || to == null) {
                continue;
            }
            Map<Object, Object> byKey = new HashMap<>();
            parents.forEach(parent -> byKey.putIfAbsent(key(get(to, parent)), parent));
            children.forEach(child -> set(back, child, byKey.get(key(get(from, child)))));
        }
    }

    private static @Nullable Object collection(Field field, List<Object> matches) {
        if (Set.class.isAssignableFrom(field.getType())) {
            return new LinkedHashSet<>(matches);
        }
        if (Collection.class.isAssignableFrom(field.getType())) {
            return new ArrayList<>(matches);
        }
        return matches.isEmpty() ? null : matches.getFirst();
    }

    private static @Nullable Object key(@Nullable Object value) {
        return value instanceof Number n && !(value instanceof BigDecimal) ? n.longValue() : value;
    }

    private static @Nullable Class<?> target(Field field) {
        if (!Collection.class.isAssignableFrom(field.getType())) {
            return field.getType();
        }
        return field.getGenericType() instanceof ParameterizedType p && p.getActualTypeArguments().length == 1
                && p.getActualTypeArguments()[0] instanceof Class<?> c ? c : null;
    }

    private static @Nullable Field field(Class<?> type, String name) {
        return fields(type).stream().filter(f -> f.getName().equals(name)).findFirst().orElse(null);
    }

    private static List<Field> fields(Class<?> type) {
        Map<String, Field> fields = new LinkedHashMap<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                fields.putIfAbsent(field.getName(), field);
            }
        }
        return List.copyOf(fields.values());
    }

    private static @Nullable Object get(Field field, Object instance) {
        try {
            field.setAccessible(true);
            return field.get(instance);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not read " + field, e);
        }
    }

    private static void set(Field field, Object instance, @Nullable Object value) {
        try {
            field.setAccessible(true);
            field.set(instance, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not set " + field, e);
        }
    }
}
