package io.github.serfom256.intentflow.core.pipeline.impl;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class EntityProperties {

    private static final ClassValue<Map<String, Optional<MethodHandle>>> CACHE = new ClassValue<>() {
        @Override
        protected Map<String, Optional<MethodHandle>> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    static boolean has(Class<?> type, String property) {
        return handle(type, property).isPresent();
    }

    static @Nullable Object read(Object entity, String property) {
        MethodHandle handle = handle(entity.getClass(), property).orElseThrow(() -> new IllegalArgumentException(
                entity.getClass().getName() + " has no property '" + property + "'"));
        return invoke(handle, entity);
    }

    private static Optional<MethodHandle> handle(Class<?> type, String property) {
        return CACHE.get(type).computeIfAbsent(property, p -> find(type, p));
    }

    private static @Nullable Object invoke(MethodHandle handle, Object entity) {
        try {
            return handle.invoke(entity);
        } catch (Throwable e) {
            throw new IllegalStateException("cannot read property of " + entity.getClass().getName(), e);
        }
    }

    private static Optional<MethodHandle> find(Class<?> type, String property) {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        try {
            if (type.isRecord()) {
                for (RecordComponent rc : type.getRecordComponents()) {
                    if (rc.getName().equals(property)) {
                        Method accessor = rc.getAccessor();
                        accessor.setAccessible(true);
                        return Optional.of(lookup.unreflect(accessor));
                    }
                }
            }
            String suffix = Character.toUpperCase(property.charAt(0)) + property.substring(1);
            for (String name : new String[]{"get" + suffix, "is" + suffix}) {
                Method getter = publicNoArg(type, name);
                if (getter != null) {
                    getter.setAccessible(true);
                    return Optional.of(lookup.unreflect(getter));
                }
            }
            Method accessor = publicNoArg(type, property);
            if (accessor != null) {
                accessor.setAccessible(true);
                return Optional.of(lookup.unreflect(accessor));
            }
            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.getName().equals(property) && !Modifier.isStatic(f.getModifiers())) {
                        f.setAccessible(true);
                        return Optional.of(lookup.unreflectGetter(f));
                    }
                }
            }
            return Optional.empty();
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("cannot access " + type.getName() + "." + property, e);
        }
    }

    private static @Nullable Method publicNoArg(Class<?> type, String name) {
        try {
            Method m = type.getMethod(name);
            return Modifier.isStatic(m.getModifiers()) || m.getReturnType() == void.class ? null : m;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
