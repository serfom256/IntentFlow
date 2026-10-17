package io.github.serfom256.intentflow.core.catalog;

public record DiscoveredRelation(String name, Class<?> target, String fromColumn, String toColumn) {
}
