package io.github.serfom256.intentflow.core.schema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record TableFacts(List<String> primaryKey, List<List<String>> uniqueKeys, Set<String> notNull,
        Map<String, List<String>> exactValues, Set<String> zonedTimestamps, Map<String, List<String>> ordinalValues) {

    public static final TableFacts UNKNOWN = new TableFacts(List.of(), List.of(), Set.of(), Map.of(), Set.of(),
            Map.of());

    public TableFacts(List<String> primaryKey, List<List<String>> uniqueKeys, Set<String> notNull,
            Map<String, List<String>> exactValues) {
        this(primaryKey, uniqueKeys, notNull, exactValues, Set.of(), Map.of());
    }

    public TableFacts(List<String> primaryKey, List<List<String>> uniqueKeys, Set<String> notNull,
            Map<String, List<String>> exactValues, Set<String> zonedTimestamps) {
        this(primaryKey, uniqueKeys, notNull, exactValues, zonedTimestamps, Map.of());
    }

    public TableFacts {
        primaryKey = List.copyOf(primaryKey);
        uniqueKeys = uniqueKeys.stream().<List<String>>map(List::copyOf).toList();
        notNull = Set.copyOf(notNull);
        exactValues = Map.copyOf(exactValues);
        zonedTimestamps = Set.copyOf(zonedTimestamps);
        ordinalValues = Map.copyOf(ordinalValues);
    }

    public static TableFacts primaryKey(List<String> columns) {
        return new TableFacts(columns, List.of(), Set.of(), Map.of());
    }

    public boolean isNotNull(String column) {
        return contains(notNull, column) || contains(primaryKey, column);
    }

    public boolean isZoned(String column) {
        return contains(zonedTimestamps, column);
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
        return lookup(exactValues, column);
    }

    public List<String> ordinalValues(String column) {
        return lookup(ordinalValues, column);
    }

    public TableFacts merge(TableFacts other) {
        List<List<String>> keys = new ArrayList<>(uniqueKeys);
        other.uniqueKeys.stream().filter(k -> !keys.contains(k)).forEach(keys::add);
        Set<String> columns = new LinkedHashSet<>(notNull);
        other.notNull.stream().filter(c -> !contains(columns, c)).forEach(columns::add);
        Set<String> zoned = new LinkedHashSet<>(zonedTimestamps);
        other.zonedTimestamps.stream().filter(c -> !contains(zoned, c)).forEach(zoned::add);
        return new TableFacts(primaryKey.isEmpty() ? other.primaryKey : primaryKey, keys, columns,
                merge(exactValues, other.exactValues), zoned, merge(ordinalValues, other.ordinalValues));
    }

    private static Map<String, List<String>> merge(Map<String, List<String>> own, Map<String, List<String>> other) {
        Map<String, List<String>> values = new LinkedHashMap<>(own);
        other.forEach((column, list) -> {
            if (lookup(own, column).isEmpty()) {
                values.put(column, list);
            }
        });
        return values;
    }

    private static List<String> lookup(Map<String, List<String>> values, String column) {
        return values.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(column))
                .map(Map.Entry::getValue).findFirst().orElse(List.of());
    }

    private static boolean contains(Collection<String> columns, String column) {
        return columns.stream().anyMatch(c -> c.equalsIgnoreCase(column));
    }
}
