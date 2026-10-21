package io.github.serfom256.intentflow.core.pipeline.impl;

import io.github.serfom256.intentflow.core.catalog.FieldMapping;
import io.github.serfom256.intentflow.core.catalog.ServiceCatalog;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.catalog.ServiceRelation;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

public final class VisibleEntities {

    private VisibleEntities() {
    }

    public static void check(ServiceCatalog catalog) {
        List<String> problems = new ArrayList<>();
        for (ServiceName name : catalog.names()) {
            Class<?> type = catalog.get(name).resultType();
            if (!type.isRecord() && (type.isInterface() || Modifier.isAbstract(type.getModifiers())
                    || (noArgConstructor(type) == null && allFieldsConstructor(type) == null))) {
                problems.add(type.getName());
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Returning only visible fields needs records, or classes with a no-arg "
                    + "constructor or a constructor taking every field in order; these cannot be copied: "
                    + String.join(", ", problems));
        }
    }

    static <T> List<T> of(List<T> items, ServiceDescriptor service, ServiceCatalog view) {
        ServiceDescriptor visible = view.find(service.name()).orElse(service);
        List<T> copies = new ArrayList<>(items.size());
        for (T item : items) {
            @SuppressWarnings("unchecked")
            T copy = (T) copy(item, visible, view, true);
            copies.add(copy);
        }
        return copies;
    }

    private static Object copy(Object item, ServiceDescriptor service, ServiceCatalog view, boolean withRelations) {
        Class<?> type = generated(item.getClass()) ? service.resultType() : item.getClass();
        Set<String> properties = properties(service);
        Map<String, ServiceDescriptor> relations = withRelations ? relations(service, view) : Map.of();
        if (type.isRecord()) {
            RecordComponent[] components = type.getRecordComponents();
            Object[] args = new Object[components.length];
            Class<?>[] types = new Class<?>[components.length];
            for (int i = 0; i < components.length; i++) {
                RecordComponent component = components[i];
                types[i] = component.getType();
                args[i] = value(component.getName(), component.getType(),
                        () -> invoke(component, item), properties, relations, view);
            }
            return construct(constructor(type, types), args);
        }
        List<Field> fields = instanceFields(type);
        Constructor<?> noArg = noArgConstructor(type);
        if (noArg != null) {
            Object copy = construct(noArg);
            for (Field field : fields) {
                set(field, copy, value(field.getName(), field.getType(), () -> get(field, item), properties,
                        relations, view));
            }
            return copy;
        }
        Constructor<?> all = Objects.requireNonNull(allFieldsConstructor(type), type.getName());
        Object[] args = fields.stream().map(field -> value(field.getName(), field.getType(), () -> get(field, item),
                properties, relations, view)).toArray();
        return construct(all, args);
    }

    private static @Nullable Object value(String name, Class<?> type, Supplier<@Nullable Object> reader,
            Set<String> properties,
            Map<String, ServiceDescriptor> relations, ServiceCatalog view) {
        if (properties.contains(name)) {
            return reader.get();
        }
        ServiceDescriptor target = relations.get(name);
        if (target != null) {
            try {
                return related(reader.get(), target, view);
            } catch (RuntimeException notLoaded) {
                return defaultValue(type);
            }
        }
        return defaultValue(type);
    }

    private static @Nullable Object related(@Nullable Object value, ServiceDescriptor target, ServiceCatalog view) {
        if (value == null) {
            return null;
        }
        if (value instanceof Collection<?> children) {
            Collection<Object> copies = value instanceof Set<?> ? new LinkedHashSet<>() : new ArrayList<>();
            for (Object child : children) {
                if (child != null && !generated(child.getClass())) {
                    copies.add(copy(child, target, view, false));
                }
            }
            return copies;
        }
        return generated(value.getClass()) ? null : copy(value, target, view, false);
    }

    private static Set<String> properties(ServiceDescriptor service) {
        Set<String> properties = new LinkedHashSet<>();
        for (FieldMapping field : service.fields()) {
            if (field.property() != null) {
                properties.add(field.property());
            }
        }
        return properties;
    }

    private static Map<String, ServiceDescriptor> relations(ServiceDescriptor service, ServiceCatalog view) {
        Map<String, ServiceDescriptor> relations = new LinkedHashMap<>();
        for (ServiceRelation relation : service.relations()) {
            view.find(relation.target()).ifPresent(target -> relations.put(relation.name(), target));
        }
        return relations;
    }

    private static boolean generated(Class<?> type) {
        String name = type.getName();
        return name.contains("$$") || name.contains("$HibernateProxy") || name.contains("$ByteBuddy");
    }

    private static List<Field> instanceFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        List<Class<?>> hierarchy = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            hierarchy.addFirst(c);
        }
        for (Class<?> c : hierarchy) {
            Arrays.stream(c.getDeclaredFields()).filter(f -> !Modifier.isStatic(f.getModifiers()) && !f.isSynthetic())
                    .forEach(fields::add);
        }
        return fields;
    }

    private static @Nullable Constructor<?> noArgConstructor(Class<?> type) {
        try {
            return type.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static @Nullable Constructor<?> allFieldsConstructor(Class<?> type) {
        Class<?>[] types = instanceFields(type).stream().map(Field::getType).toArray(Class<?>[]::new);
        return types.length == 0 ? null : constructor(type, types);
    }

    private static @Nullable Constructor<?> constructor(Class<?> type, Class<?>[] types) {
        try {
            return type.getDeclaredConstructor(types);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static @Nullable Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        return 0;
    }

    private static Object construct(@Nullable Constructor<?> constructor, Object... args) {
        if (constructor == null) {
            throw new IllegalStateException("No constructor to copy the entity with");
        }
        try {
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not copy " + constructor.getDeclaringClass().getName(), e);
        }
    }

    private static @Nullable Object invoke(RecordComponent component, Object item) {
        try {
            component.getAccessor().setAccessible(true);
            return component.getAccessor().invoke(item);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not read " + component, e);
        }
    }

    private static @Nullable Object get(Field field, Object item) {
        try {
            field.setAccessible(true);
            return field.get(item);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not read " + field, e);
        }
    }

    private static void set(Field field, Object copy, @Nullable Object value) {
        if (Modifier.isFinal(field.getModifiers())) {
            return;
        }
        try {
            field.setAccessible(true);
            field.set(copy, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not set " + field, e);
        }
    }
}
