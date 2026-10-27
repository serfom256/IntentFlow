package io.github.serfom256.intentflow.openmapper;

import io.github.serfom256.annotations.entity.Joined;
import io.github.serfom256.annotations.entity.Model;
import io.github.serfom256.intentflow.core.catalog.DiscoveredRelation;
import io.github.serfom256.intentflow.core.catalog.NameResolver;
import io.github.serfom256.intentflow.core.schema.TableFacts;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class OpenMapperNameResolver implements NameResolver {

    @Override
    public Optional<String> columnName(Field field) {
        io.github.serfom256.annotations.entity.Field mapping = field
                .getAnnotation(io.github.serfom256.annotations.entity.Field.class);
        if (mapping == null) {
            return Optional.empty();
        }
        return Optional.of(mapping.name().isEmpty() ? field.getName() : mapping.name());
    }

    @Override
    public List<DiscoveredRelation> relations(Class<?> type) {
        List<DiscoveredRelation> relations = new ArrayList<>();
        for (Field field : fields(type)) {
            Joined joined = field.getAnnotation(Joined.class);
            if (joined == null) {
                continue;
            }
            Class<?> target = List.class.isAssignableFrom(field.getType()) ? elementType(field) : field.getType();
            if (target == null) {
                continue;
            }
            Optional<String> from = column(type, joined.joinBy());
            Optional<String> to = column(target, joined.to());
            if (from.isPresent() && to.isPresent()) {
                relations.add(new DiscoveredRelation(field.getName(), target, from.get(), to.get()));
            }
        }
        return relations;
    }

    @Override
    public TableFacts facts(Class<?> type) {
        Model model = type.getAnnotation(Model.class);
        if (model == null || model.primaryKey().isEmpty()) {
            return TableFacts.UNKNOWN;
        }
        return column(type, model.primaryKey()).map(c -> TableFacts.primaryKey(List.of(c))).orElse(TableFacts.UNKNOWN);
    }

    private Optional<String> column(Class<?> type, String javaField) {
        return fields(type).stream().filter(f -> f.getName().equals(javaField)).findFirst().flatMap(this::columnName);
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
