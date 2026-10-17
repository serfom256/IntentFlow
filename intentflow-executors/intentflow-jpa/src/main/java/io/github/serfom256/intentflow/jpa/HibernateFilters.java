package io.github.serfom256.intentflow.jpa;

import io.github.serfom256.intentflow.core.pipeline.RuntimeRestriction;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.hibernate.annotations.Filters;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

final class HibernateFilters {

    private HibernateFilters() {
    }

    static List<RuntimeRestriction> enabled(Session session, Class<?> entityType) {
        List<RuntimeRestriction> restrictions = new ArrayList<>();
        for (org.hibernate.annotations.Filter declared : declared(entityType)) {
            Filter filter = session.getEnabledFilter(declared.name());
            if (filter == null) {
                continue;
            }
            String condition = declared.condition().isBlank()
                    ? filter.getFilterDefinition().getDefaultFilterCondition()
                    : declared.condition();
            if (condition != null && !condition.isBlank()) {
                restrictions.add(bind(condition.replace("{alias}.", "").replace("{alias}", ""), filter));
            }
        }
        return restrictions;
    }

    private static List<org.hibernate.annotations.Filter> declared(Class<?> entityType) {
        List<org.hibernate.annotations.Filter> filters = new ArrayList<>();
        for (Class<?> c = entityType; c != null && c != Object.class; c = c.getSuperclass()) {
            filters.addAll(List.of(c.getAnnotationsByType(org.hibernate.annotations.Filter.class)));
            Filters container = c.getAnnotation(Filters.class);
            if (container != null) {
                filters.addAll(List.of(container.value()));
            }
        }
        return filters.stream().distinct().toList();
    }

    static RuntimeRestriction bind(String condition, Filter filter) {
        List<String> segments = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        int i = 0;
        while (i < condition.length()) {
            char c = condition.charAt(i);
            if (c == '\'') {
                quoted = !quoted;
            }
            boolean parameter = !quoted && c == ':' && i + 1 < condition.length()
                    && Character.isJavaIdentifierStart(condition.charAt(i + 1))
                    && (i == 0 || condition.charAt(i - 1) != ':');
            if (!parameter) {
                current.append(c);
                i++;
                continue;
            }
            int end = i + 1;
            while (end < condition.length() && Character.isJavaIdentifierPart(condition.charAt(end))) {
                end++;
            }
            Object value = value(filter, condition.substring(i + 1, end));
            List<?> expanded = value instanceof Collection<?> many ? List.copyOf(many) : List.of(value);
            for (int k = 0; k < expanded.size(); k++) {
                if (k > 0) {
                    current.append(", ");
                }
                segments.add(current.toString());
                current.setLength(0);
                values.add(expanded.get(k));
            }
            i = end;
        }
        segments.add(current.toString());
        return new RuntimeRestriction(segments, values);
    }

    private static Object value(Filter filter, String name) {
        Object value = invoke(filter, "getParameterValue", name);
        if (value == null) {
            value = invoke(filter, "getParameter", name);
        }
        if (value == null) {
            throw new IllegalStateException("Hibernate filter '" + filter.getName() + "' has no value for :" + name);
        }
        return value;
    }

    private static @Nullable Object invoke(Filter filter, String method, String name) {
        try {
            Method getter = filter.getClass().getMethod(method, String.class);
            return getter.invoke(filter, name);
        } catch (NoSuchMethodException e) {
            return null;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not read parameter :" + name + " of filter " + filter.getName(), e);
        }
    }
}
