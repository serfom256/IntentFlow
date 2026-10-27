package io.github.serfom256.intentflow.jimmer;

import io.github.serfom256.intentflow.core.catalog.DiscoveredRelation;
import io.github.serfom256.intentflow.core.catalog.NameResolver;
import io.github.serfom256.intentflow.core.catalog.factories.ColumnNames;
import io.github.serfom256.intentflow.core.schema.TableFacts;
import org.babyfish.jimmer.meta.ImmutableProp;
import org.babyfish.jimmer.meta.ImmutableType;
import org.babyfish.jimmer.meta.LogicalDeletedInfo;
import org.babyfish.jimmer.meta.TargetLevel;
import org.babyfish.jimmer.sql.Column;
import org.babyfish.jimmer.sql.Id;
import org.babyfish.jimmer.sql.JoinColumn;
import org.babyfish.jimmer.sql.ManyToOne;
import org.babyfish.jimmer.sql.OneToMany;
import org.babyfish.jimmer.sql.OneToOne;
import org.babyfish.jimmer.sql.Table;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class JimmerNameResolver implements NameResolver {

    @Override
    public Optional<String> tableName(Class<?> type) {
        Table table = type.getAnnotation(Table.class);
        return table == null || table.name().isEmpty() ? Optional.empty() : Optional.of(table.name());
    }

    @Override
    public Optional<String> columnName(Method method) {
        Column column = method.getAnnotation(Column.class);
        return column == null || column.name().isEmpty() ? Optional.empty() : Optional.of(column.name());
    }

    @Override
    public List<DiscoveredRelation> relations(Class<?> type) {
        List<DiscoveredRelation> relations = new ArrayList<>();
        if (!type.isInterface()) {
            return relations;
        }
        for (Method method : type.getMethods()) {
            OneToOne oneToOne = method.getAnnotation(OneToOne.class);
            OneToMany oneToMany = method.getAnnotation(OneToMany.class);
            if (method.isAnnotationPresent(ManyToOne.class) || (oneToOne != null && oneToOne.mappedBy().isEmpty())) {
                relations.add(owning(method.getName(), method.getReturnType(), method.getAnnotation(JoinColumn.class)));
            } else if (oneToMany != null && !oneToMany.mappedBy().isEmpty()) {
                inverse(type, method, elementType(method), oneToMany.mappedBy()).ifPresent(relations::add);
            } else if (oneToOne != null) {
                inverse(type, method, method.getReturnType(), oneToOne.mappedBy()).ifPresent(relations::add);
            }
        }
        return relations;
    }

    @Override
    public TableFacts facts(Class<?> type) {
        if (!type.isInterface()) {
            return TableFacts.UNKNOWN;
        }
        ImmutableType immutable;
        try {
            immutable = ImmutableType.get(type);
        } catch (RuntimeException notAnEntity) {
            return TableFacts.UNKNOWN;
        }
        Set<String> notNull = new LinkedHashSet<>();
        for (ImmutableProp prop : immutable.getSelectableProps().values()) {
            if (prop.isColumnDefinition() && !prop.isNullable() && prop.isScalar(TargetLevel.ENTITY)) {
                column(type, prop.getName()).ifPresent(notNull::add);
            }
        }
        return new TableFacts(List.of(idColumn(type)), List.of(), notNull, Map.of());
    }

    @Override
    public Optional<String> restriction(Class<?> type) {
        if (!type.isInterface()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(logicalDeleted(ImmutableType.get(type).getLogicalDeletedInfo()));
        } catch (RuntimeException notAnEntity) {
            return Optional.empty();
        }
    }

    private Optional<String> column(Class<?> type, String property) {
        try {
            Method method = type.getMethod(property);
            return columnName(method).or(() -> Optional.of(ColumnNames.snakeCase(property)));
        } catch (NoSuchMethodException e) {
            return Optional.empty();
        }
    }

    private static @Nullable String logicalDeleted(@Nullable LogicalDeletedInfo info) {
        if (info == null) {
            return null;
        }
        String column = info.getColumnName();
        return switch (info.getAction()) {
        case LogicalDeletedInfo.Action.Eq eq -> literal(eq.getValue()).map(v -> column + " = " + v).orElse(null);
        case LogicalDeletedInfo.Action.Ne ne -> literal(ne.getValue()).map(v -> column + " <> " + v).orElse(null);
        case LogicalDeletedInfo.Action.IsNull n -> column + " IS NULL";
        case LogicalDeletedInfo.Action.IsNotNull n -> column + " IS NOT NULL";
        default -> null;
        };
    }

    private static Optional<String> literal(@Nullable Object value) {
        return switch (value) {
        case Boolean b -> Optional.of(b ? "TRUE" : "FALSE");
        case Number n -> Optional.of(n.toString());
        case String s -> Optional.of("'" + s.replace("'", "''") + "'");
        case Enum<?> e -> Optional.of("'" + e.name() + "'");
        case null, default -> Optional.empty();
        };
    }

    private static DiscoveredRelation owning(String name, Class<?> target, @Nullable JoinColumn join) {
        String to = join != null && !join.referencedColumnName().isEmpty() ? join.referencedColumnName()
                : idColumn(target);
        String from = join != null && !join.name().isEmpty() ? join.name() : ColumnNames.snakeCase(name) + "_id";
        return new DiscoveredRelation(name, target, from, to);
    }

    private static Optional<DiscoveredRelation> inverse(Class<?> type, Method method, @Nullable Class<?> target,
            String mappedBy) {
        if (target == null) {
            return Optional.empty();
        }
        try {
            Method owner = target.getMethod(mappedBy);
            DiscoveredRelation owning = owning(mappedBy, type, owner.getAnnotation(JoinColumn.class));
            return Optional
                    .of(new DiscoveredRelation(method.getName(), target, owning.toColumn(), owning.fromColumn()));
        } catch (NoSuchMethodException e) {
            return Optional.empty();
        }
    }

    private static String idColumn(Class<?> type) {
        for (Method method : type.getMethods()) {
            if (method.isAnnotationPresent(Id.class)) {
                Column column = method.getAnnotation(Column.class);
                return column != null && !column.name().isEmpty() ? column.name()
                        : ColumnNames.snakeCase(method.getName());
            }
        }
        return "id";
    }

    private static @Nullable Class<?> elementType(Method method) {
        if (method.getGenericReturnType() instanceof ParameterizedType p && p.getActualTypeArguments().length == 1
                && p.getActualTypeArguments()[0] instanceof Class<?> c) {
            return c;
        }
        return null;
    }
}
