package io.github.serfom256.intentflow.jpa;

import io.github.serfom256.intentflow.core.catalog.DiscoveredRelation;
import io.github.serfom256.intentflow.core.catalog.NameResolver;
import io.github.serfom256.intentflow.core.catalog.factories.ColumnNames;
import io.github.serfom256.intentflow.core.schema.TableFacts;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

public final class JpaNameResolver implements NameResolver {

    private static final Map<String, String> RESTRICTIONS = Map.of("org.hibernate.annotations.SQLRestriction", "value",
            "org.hibernate.annotations.Where", "clause");

    private final Supplier<Collection<Class<?>>> entities;

    public JpaNameResolver() {
        this(List::of);
    }

    public JpaNameResolver(Supplier<Collection<Class<?>>> entities) {
        this.entities = entities;
    }

    @Override
    public Optional<String> tableName(Class<?> type) {
        Class<?> root = root(type);
        Table table = (type.getAnnotation(Table.class) == null && singleTable(root) ? root : type)
                .getAnnotation(Table.class);
        if (table == null || table.name().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(table.schema().isEmpty() ? table.name() : table.schema() + "." + table.name());
    }

    @Override
    public TableFacts facts(Class<?> type) {
        if (!type.isAnnotationPresent(Entity.class)) {
            return TableFacts.UNKNOWN;
        }
        List<String> primaryKey = new ArrayList<>();
        Set<String> notNull = new LinkedHashSet<>();
        Map<String, List<String>> ordinals = new LinkedHashMap<>();
        for (Field field : fields(type)) {
            if (Modifier.isStatic(field.getModifiers()) || field.isAnnotationPresent(Transient.class)
                    || field.isAnnotationPresent(EmbeddedId.class) || isAssociation(field)) {
                continue;
            }
            Column column = field.getAnnotation(Column.class);
            String name = column != null && !column.name().isEmpty() ? column.name()
                    : ColumnNames.snakeCase(field.getName());
            if (field.isAnnotationPresent(Id.class)) {
                primaryKey.add(name);
            }
            if (field.isAnnotationPresent(Id.class) || field.getType().isPrimitive()
                    || (column != null && !column.nullable())) {
                notNull.add(name);
            }
            if (storedAsOrdinal(field)) {
                ordinals.put(name,
                        Arrays.stream(field.getType().getEnumConstants()).map(c -> ((Enum<?>) c).name()).toList());
            }
        }
        return new TableFacts(primaryKey, List.of(), notNull, Map.of(), Set.of(), ordinals);
    }

    private static boolean storedAsOrdinal(Field field) {
        if (!field.getType().isEnum() || field.isAnnotationPresent(Convert.class)
                || Arrays.stream(field.getAnnotations())
                        .anyMatch(a -> a.annotationType().getSimpleName().startsWith("JdbcType"))) {
            return false;
        }
        Enumerated enumerated = field.getAnnotation(Enumerated.class);
        return enumerated == null || enumerated.value() == EnumType.ORDINAL;
    }

    @Override
    public Optional<String> restriction(Class<?> type) {
        String declared = declaredRestriction(type);
        String discriminator = discriminator(type);
        if (declared == null || discriminator == null) {
            return Optional.ofNullable(declared != null ? declared : discriminator);
        }
        return Optional.of("(" + declared + ") AND " + discriminator);
    }

    private static Class<?> root(Class<?> type) {
        Class<?> root = type;
        while (root.getSuperclass() != null && root.getSuperclass().isAnnotationPresent(Entity.class)) {
            root = root.getSuperclass();
        }
        return root;
    }

    private static boolean singleTable(Class<?> root) {
        Inheritance inheritance = root.getAnnotation(Inheritance.class);
        return inheritance == null || inheritance.strategy() == InheritanceType.SINGLE_TABLE;
    }

    private @Nullable String discriminator(Class<?> type) {
        Class<?> root = root(type);
        if (root == type || !singleTable(root) || Arrays.stream(root.getAnnotations())
                .anyMatch(a -> a.annotationType().getSimpleName().equals("DiscriminatorFormula"))) {
            return null;
        }
        DiscriminatorColumn column = root.getAnnotation(DiscriminatorColumn.class);
        String name = column == null || column.name().isEmpty() ? "DTYPE" : column.name();
        DiscriminatorType kind = column == null ? DiscriminatorType.STRING : column.discriminatorType();
        List<String> values = new ArrayList<>();
        for (Class<?> entity : concreteTypes(type)) {
            String value = discriminatorValue(entity, kind);
            if (value == null) {
                return null;
            }
            values.add(value);
        }
        if (values.isEmpty()) {
            return null;
        }
        return name + (values.size() == 1 ? " = " + values.getFirst() : " IN (" + String.join(", ", values) + ")");
    }

    private List<Class<?>> concreteTypes(Class<?> type) {
        List<Class<?>> types = new ArrayList<>();
        if (!Modifier.isAbstract(type.getModifiers())) {
            types.add(type);
        }
        entities.get().stream()
                .filter(e -> e != type && type.isAssignableFrom(e) && e.isAnnotationPresent(Entity.class)
                        && !Modifier.isAbstract(e.getModifiers()))
                .sorted(Comparator.comparing(Class::getName)).forEach(types::add);
        return types;
    }

    private static @Nullable String discriminatorValue(Class<?> entity, DiscriminatorType kind) {
        DiscriminatorValue declared = entity.getAnnotation(DiscriminatorValue.class);
        String value = declared != null ? declared.value()
                : kind == DiscriminatorType.STRING ? entityName(entity) : null;
        if (value == null) {
            return null;
        }
        if (kind == DiscriminatorType.INTEGER) {
            return value.strip().matches("-?\\d+") ? value.strip() : null;
        }
        return "'" + value.replace("'", "''") + "'";
    }

    private static String entityName(Class<?> entity) {
        Entity annotation = entity.getAnnotation(Entity.class);
        String className = entity.getName();
        return annotation == null || annotation.name().isEmpty() ? className.substring(className.lastIndexOf('.') + 1)
                : annotation.name();
    }

    private static @Nullable String declaredRestriction(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Annotation annotation : c.getAnnotations()) {
                String attribute = RESTRICTIONS.get(annotation.annotationType().getName());
                if (attribute != null) {
                    try {
                        Object sql = annotation.annotationType().getMethod(attribute).invoke(annotation);
                        if (sql instanceof String text && !text.isBlank()) {
                            return text;
                        }
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException("Could not read " + annotation + " on " + type.getName(), e);
                    }
                }
            }
        }
        return null;
    }

    private static boolean isAssociation(Field field) {
        return field.isAnnotationPresent(ManyToOne.class) || field.isAnnotationPresent(OneToOne.class)
                || field.isAnnotationPresent(OneToMany.class) || field.isAnnotationPresent(ManyToMany.class);
    }

    @Override
    public Optional<String> columnName(Field field) {
        Column column = field.getAnnotation(Column.class);
        return column == null || column.name().isEmpty() ? Optional.empty() : Optional.of(column.name());
    }

    @Override
    public List<DiscoveredRelation> relations(Class<?> type) {
        List<DiscoveredRelation> relations = new ArrayList<>();
        for (Field field : fields(type)) {
            ManyToOne manyToOne = field.getAnnotation(ManyToOne.class);
            OneToOne oneToOne = field.getAnnotation(OneToOne.class);
            OneToMany oneToMany = field.getAnnotation(OneToMany.class);
            if (manyToOne != null || (oneToOne != null && oneToOne.mappedBy().isEmpty())) {
                Class<?> target = field.getType();
                relations.add(owning(field.getName(), target, field.getAnnotation(JoinColumn.class)));
            } else if (oneToMany != null && !oneToMany.mappedBy().isEmpty()) {
                inverse(type, field, elementType(field), oneToMany.mappedBy()).ifPresent(relations::add);
            } else if (oneToOne != null) {
                inverse(type, field, field.getType(), oneToOne.mappedBy()).ifPresent(relations::add);
            }
        }
        return relations;
    }

    private static DiscoveredRelation owning(String name, Class<?> target, @Nullable JoinColumn join) {
        String to = join != null && !join.referencedColumnName().isEmpty() ? join.referencedColumnName()
                : idColumn(target);
        String from = join != null && !join.name().isEmpty() ? join.name() : ColumnNames.snakeCase(name) + "_" + to;
        return new DiscoveredRelation(name, target, from, to);
    }

    private static Optional<DiscoveredRelation> inverse(Class<?> type, Field field, @Nullable Class<?> target,
            String mappedBy) {
        if (target == null) {
            return Optional.empty();
        }
        return fields(target).stream().filter(f -> f.getName().equals(mappedBy)).findFirst().map(owner -> {
            DiscoveredRelation owning = owning(mappedBy, type, owner.getAnnotation(JoinColumn.class));
            return new DiscoveredRelation(field.getName(), target, owning.toColumn(), owning.fromColumn());
        });
    }

    private static String idColumn(Class<?> type) {
        for (Field field : fields(type)) {
            if (field.isAnnotationPresent(Id.class)) {
                Column column = field.getAnnotation(Column.class);
                return column != null && !column.name().isEmpty() ? column.name()
                        : ColumnNames.snakeCase(field.getName());
            }
        }
        return "id";
    }

    private static @Nullable Class<?> elementType(Field field) {
        if (field.getGenericType() instanceof ParameterizedType p && p.getActualTypeArguments().length == 1
                && p.getActualTypeArguments()[0] instanceof Class<?> c) {
            return c;
        }
        return null;
    }

    private static List<Field> fields(Class<?> type) {
        List<Field> out = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            out.addAll(List.of(c.getDeclaredFields()));
        }
        return out;
    }
}
