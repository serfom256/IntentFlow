package io.github.serfom256.intentflow.core.catalog.impl;

import io.github.serfom256.intentflow.core.catalog.DiscoveredRelation;
import io.github.serfom256.intentflow.core.catalog.FieldMapping;
import io.github.serfom256.intentflow.core.catalog.FieldType;
import io.github.serfom256.intentflow.core.catalog.LogicalFieldName;
import io.github.serfom256.intentflow.core.catalog.NameResolver;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.ServiceRelation;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentRelation;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.schema.TableFacts;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AnnotatedServiceReader {

    private static final Set<Class<?>> NUMBER_TYPES = Set.of(
            byte.class, short.class, int.class, long.class, float.class, double.class,
            Byte.class, Short.class, Integer.class, Long.class, Float.class, Double.class,
            BigDecimal.class, BigInteger.class);
    private static final Set<Class<?>> INTEGER_TYPES = Set.of(byte.class, short.class, int.class, long.class,
            Byte.class, Short.class, Integer.class, Long.class, BigInteger.class);

    private static final Set<Class<?>> TIMESTAMP_TYPES = Set.of(
            Instant.class, LocalDate.class, LocalDateTime.class, OffsetDateTime.class,
            ZonedDateTime.class, Date.class, java.sql.Date.class, Timestamp.class);

    public static ServiceDescriptor read(Class<?> type, List<NameResolver> resolvers) {
        IntentService service = type.getAnnotation(IntentService.class);
        if (service == null) {
            throw new IllegalArgumentException(
                    type.getName() + " is not annotated with @" + IntentService.class.getSimpleName());
        }

        List<FieldMapping> fields = declaredMappings(type, resolvers);
        if (fields.isEmpty()) {
            throw new IllegalArgumentException(
                    type.getName() + " has no @" + IntentField.class.getSimpleName() + " fields");
        }

        String table = !service.table().isEmpty() ? service.table()
                : resolve(resolvers, r -> r.tableName(type)).orElseGet(() -> snakeCase(type.getSimpleName()));
        List<ServiceRelation> relations = relations(type, fields, resolvers);
        return new DefaultServiceDescriptor(
                new DefaultServiceName(service.value()),
                service.description().isBlank() ? null : new DefaultServiceDescription(service.description()),
                new DefaultTableName(table),
                fields,
                Arrays.stream(service.defaultSort()).map(AnnotatedServiceReader::sortKey).toList(),
                new DefaultMaxTake(service.maxTake()),
                new DefaultUriTemplate(service.uri()),
                type,
                relations);
    }

    private static List<ServiceRelation> relations(Class<?> type, List<FieldMapping> fields,
            List<NameResolver> resolvers) {
        List<ServiceRelation> relations = new ArrayList<>(Arrays.stream(type.getAnnotationsByType(IntentRelation.class))
                .map(AnnotatedServiceReader::relation)
                .toList());
        Set<String> names = new HashSet<>();
        relations.forEach(r -> names.add(r.name()));
        for (NameResolver resolver : resolvers) {
            for (DiscoveredRelation discovered : resolver.relations(type)) {
                IntentService target = discovered.target().getAnnotation(IntentService.class);
                if (target == null || !names.add(discovered.name())) {
                    continue;
                }
                LogicalFieldName from = linkField(fields, discovered.fromColumn());
                LogicalFieldName to = new DefaultLogicalFieldName(
                        logicalForColumn(declaredMappings(discovered.target(), resolvers), discovered.toColumn()));
                relations.add(new DefaultServiceRelation(discovered.name(), new DefaultServiceName(target.value()),
                        from, to));
            }
        }
        return relations;
    }

    private static LogicalFieldName linkField(List<FieldMapping> fields, String column) {
        for (FieldMapping fm : fields) {
            if (fm.column().value().equals(column)) {
                return fm.logical();
            }
        }
        String logical = column;
        Set<String> taken = new HashSet<>();
        fields.forEach(fm -> taken.add(fm.logical().value()));
        while (taken.contains(logical)) {
            logical = "_" + logical;
        }
        DefaultFieldMapping hidden = new DefaultFieldMapping(new DefaultLogicalFieldName(logical),
                new DefaultColumnName(column), false, FieldType.ANY, null, List.of(), null);
        fields.add(hidden);
        return hidden.logical();
    }

    private static String logicalForColumn(List<FieldMapping> fields, String column) {
        return fields.stream().filter(fm -> fm.column().value().equals(column))
                .map(fm -> fm.logical().value()).findFirst().orElse(column);
    }

    private static List<FieldMapping> declaredMappings(Class<?> type, List<NameResolver> resolvers) {
        List<FieldMapping> fields = new ArrayList<>();
        if (type.isInterface()) {
            for (Method method : propertyMethods(type)) {
                IntentField meta = method.getAnnotation(IntentField.class);
                if (meta != null) {
                    fields.add(mapping(type, propertyName(method), method.getReturnType(), meta,
                            () -> resolve(resolvers, r -> r.columnName(method))));
                }
            }
        } else {
            for (Field field : declaredFields(type)) {
                IntentField meta = field.getAnnotation(IntentField.class);
                if (meta != null) {
                    fields.add(mapping(type, field.getName(), field.getType(), meta,
                            () -> resolve(resolvers, r -> r.columnName(field))));
                }
            }
        }
        return fields;
    }

    private static ServiceRelation relation(IntentRelation r) {
        return new DefaultServiceRelation(r.name(), new DefaultServiceName(r.service()),
                new DefaultLogicalFieldName(r.from()), new DefaultLogicalFieldName(r.to()));
    }

    private static FieldMapping mapping(Class<?> owner, String property, Class<?> javaType, IntentField meta,
            Supplier<Optional<String>> resolvedColumn) {
        String logical = meta.name().isEmpty() ? snakeCase(property) : meta.name();
        String column = !meta.column().isEmpty() ? meta.column()
                : resolvedColumn.get().orElseGet(() -> snakeCase(property));

        FieldType type = meta.type() != FieldType.ANY ? meta.type() : inferType(owner, property, javaType);
        List<String> values = meta.values().length > 0
                ? List.of(meta.values())
                : javaType.isEnum() ? enumNames(javaType) : List.of();

        return new DefaultFieldMapping(
                new DefaultLogicalFieldName(logical),
                new DefaultColumnName(column),
                meta.queryable(),
                type,
                meta.description(),
                values,
                property,
                meta.caseSensitive());
    }

    public static TableFacts facts(Class<?> type, List<NameResolver> resolvers) {
        Set<String> notNull = new HashSet<>();
        Set<String> zoned = new HashSet<>();
        Set<String> integers = new HashSet<>();
        Map<String, List<String>> exactValues = new LinkedHashMap<>();
        for (FieldMapping fm : declaredMappings(type, resolvers)) {
            Class<?> javaType = javaType(type, fm.property());
            if (javaType == null) {
                continue;
            }
            if (javaType.isPrimitive()) {
                notNull.add(fm.column().value());
            }
            if (javaType == Instant.class || javaType == OffsetDateTime.class || javaType == ZonedDateTime.class) {
                zoned.add(fm.column().value());
            }
            if (INTEGER_TYPES.contains(javaType)) {
                integers.add(fm.column().value());
            }
            IntentField meta = annotation(type, fm.property());
            if (javaType.isEnum() && meta != null && meta.values().length == 0) {
                exactValues.put(fm.column().value(), enumNames(javaType));
            }
        }
        TableFacts facts = new TableFacts(List.of(), List.of(), notNull, exactValues, zoned, Map.of(), integers);
        for (NameResolver resolver : resolvers) {
            facts = facts.merge(resolver.facts(type));
        }
        return facts;
    }

    public static Optional<String> restriction(Class<?> type, List<NameResolver> resolvers) {
        IntentService service = type.getAnnotation(IntentService.class);
        if (service != null && !service.restriction().isBlank()) {
            return Optional.of(service.restriction().strip());
        }
        return resolve(resolvers, r -> r.restriction(type));
    }

    private static @Nullable Class<?> javaType(Class<?> owner, @Nullable String property) {
        if (property == null) {
            return null;
        }
        if (owner.isInterface()) {
            return propertyMethods(owner).stream().filter(m -> propertyName(m).equals(property)).findFirst()
                    .<Class<?>>map(Method::getReturnType).orElse(null);
        }
        return declaredFields(owner).stream().filter(f -> f.getName().equals(property)).findFirst()
                .<Class<?>>map(Field::getType).orElse(null);
    }

    private static @Nullable IntentField annotation(Class<?> owner, @Nullable String property) {
        if (property == null) {
            return null;
        }
        if (owner.isInterface()) {
            return propertyMethods(owner).stream().filter(m -> propertyName(m).equals(property)).findFirst()
                    .map(m -> m.getAnnotation(IntentField.class)).orElse(null);
        }
        return declaredFields(owner).stream().filter(f -> f.getName().equals(property)).findFirst()
                .map(f -> f.getAnnotation(IntentField.class)).orElse(null);
    }

    private static Optional<String> resolve(List<NameResolver> resolvers,
            Function<NameResolver, Optional<String>> lookup) {
        for (NameResolver r : resolvers) {
            Optional<String> name = lookup.apply(r).filter(n -> !n.isBlank());
            if (name.isPresent()) {
                return name;
            }
        }
        return Optional.empty();
    }

    private static FieldType inferType(Class<?> owner, String property, Class<?> t) {
        if (t == String.class || t == char.class || t == Character.class || t == UUID.class || t.isEnum()) {
            return FieldType.TEXT;
        }
        if (NUMBER_TYPES.contains(t)) {
            return FieldType.NUMBER;
        }
        if (t == boolean.class || t == Boolean.class) {
            return FieldType.BOOLEAN;
        }
        if (TIMESTAMP_TYPES.contains(t)) {
            return FieldType.TIMESTAMP;
        }
        throw new IllegalArgumentException("cannot infer field type of " + owner.getSimpleName() + "."
                + property + " (" + t.getName() + "); set @IntentField(type = ...)");
    }

    private static List<String> enumNames(Class<?> enumType) {
        return Arrays.stream(enumType.getEnumConstants()).map(c -> ((Enum<?>) c).name()).toList();
    }

    private static SortKey sortKey(String spec) {
        String[] parts = spec.trim().split("\\s+");
        if (parts.length == 0 || parts.length > 2 || parts[0].isEmpty()) {
            throw new IllegalArgumentException("bad defaultSort entry '" + spec + "'");
        }
        SortKey.Direction direction = SortKey.Direction.ASC;
        if (parts.length == 2) {
            direction = switch (parts[1].toLowerCase(Locale.ROOT)) {
                case "asc" -> SortKey.Direction.ASC;
                case "desc" -> SortKey.Direction.DESC;
                default -> throw new IllegalArgumentException(
                        "bad sort direction in defaultSort entry '" + spec + "'");
            };
        }
        return new DefaultSortKey(new DefaultLogicalFieldName(parts[0]), direction);
    }

    private static List<Method> propertyMethods(Class<?> type) {
        Deque<Class<?>> hierarchy = new ArrayDeque<>();
        collectInterfaces(type, hierarchy);
        List<Method> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Class<?> c : hierarchy) {
            Method[] methods = c.getDeclaredMethods();
            Arrays.sort(methods, Comparator.comparing(AnnotatedServiceReader::propertyName));
            for (Method m : methods) {
                if (m.getParameterCount() == 0 && m.getReturnType() != void.class
                        && !Modifier.isStatic(m.getModifiers())
                        && !m.isSynthetic() && seen.add(m.getName())) {
                    out.add(m);
                }
            }
        }
        return out;
    }

    private static void collectInterfaces(Class<?> type, Deque<Class<?>> out) {
        if (out.contains(type)) {
            return;
        }
        for (Class<?> parent : type.getInterfaces()) {
            collectInterfaces(parent, out);
        }
        out.addLast(type);
    }

    static String propertyName(Method method) {
        String name = method.getName();
        for (String prefix : new String[] { "get", "is" }) {
            if (name.length() > prefix.length() && name.startsWith(prefix)
                    && Character.isUpperCase(name.charAt(prefix.length()))) {
                return Character.toLowerCase(name.charAt(prefix.length())) + name.substring(prefix.length() + 1);
            }
        }
        return name;
    }

    private static List<Field> declaredFields(Class<?> type) {
        if (type.isRecord()) {
            List<Field> out = new ArrayList<>();
            for (RecordComponent rc : type.getRecordComponents()) {
                try {
                    out.add(type.getDeclaredField(rc.getName()));
                } catch (NoSuchFieldException e) {
                    throw new IllegalStateException("record component without field: " + rc.getName(), e);
                }
            }
            return out;
        }
        Deque<Class<?>> hierarchy = new ArrayDeque<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            hierarchy.push(c);
        }
        List<Field> out = new ArrayList<>();
        for (Class<?> c : hierarchy) {
            for (Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) && !f.isSynthetic()) {
                    out.add(f);
                }
            }
        }
        return out;
    }

    public static String snakeCase(String name) {
        StringBuilder sb = new StringBuilder(name.length() + 4);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                boolean prevLowerOrDigit = i > 0
                        && (Character.isLowerCase(name.charAt(i - 1)) || Character.isDigit(name.charAt(i - 1)));
                boolean acronymEnd = i > 0 && i + 1 < name.length()
                        && Character.isUpperCase(name.charAt(i - 1)) && Character.isLowerCase(name.charAt(i + 1));
                if (prevLowerOrDigit || acronymEnd) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
