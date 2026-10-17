package io.github.serfom256.intentflow.core.schema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record TableFacts(List<String> primaryKey, List<List<String>> uniqueKeys, Set<String> notNull,
        Map<String, List<String>> exactValues) {

    public static final TableFacts UNKNOWN = new TableFacts(List.of(), List.of(), Set.of(), Map.of());

    public TableFacts {
        primaryKey = List.copyOf(primaryKey);
        uniqueKeys = uniqueKeys.stream().<List<String>>map(List::copyOf).toList();
        notNull = Set.copyOf(notNull);
        exactValues = Map.copyOf(exactValues);
    }

    public static TableFacts primaryKey(List<String> columns) {
        return new TableFacts(columns, List.of(), Set.of(), Map.of());
    }

    public boolean isNotNull(String column) {
        return contains(notNull, column) || contains(primaryKey, column);
    }

    public boolean isUnique(String column) {
        return isUnique(List.of(column));
    }

    public boolean isUnique(Collection<String> columns) {
        if (!primaryKey.isEmpty() && primaryKey.stream().allMatch(k -> contains(columns, k))) {
            return true;
        }
        return uniqueKeys.stream().anyMatch(key -> key.stream().allMatch(k -> contains(columns, k)));
    }

    public List<String> exactValues(String column) {
        return exactValues.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(column))
                .map(Map.Entry::getValue).findFirst().orElse(List.of());
    }

    public TableFacts merge(TableFacts other) {
        List<List<String>> keys = new ArrayList<>(uniqueKeys);
        other.uniqueKeys.stream().filter(k -> !keys.contains(k)).forEach(keys::add);
        Set<String> columns = new LinkedHashSet<>(notNull);
        other.notNull.stream().filter(c -> !contains(columns, c)).forEach(columns::add);
        Map<String, List<String>> values = new LinkedHashMap<>(exactValues);
        other.exactValues.forEach((column, list) -> {
            if (exactValues(column).isEmpty()) {
                values.put(column, list);
            }
        });
        return new TableFacts(primaryKey.isEmpty() ? other.primaryKey : primaryKey, keys, columns, values);
    }

    private static boolean contains(Collection<String> columns, String column) {
        return columns.stream().anyMatch(c -> c.equalsIgnoreCase(column));
    }
}
