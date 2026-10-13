package io.github.serfom256.intentflow.core.pipeline.factories;

import io.github.serfom256.intentflow.core.pipeline.SqlDialect;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SqlDialects {

    private static final Map<String, String> ALIASES = Map.of(
            "postgres", "postgresql",
            "h2", "postgresql",
            "mariadb", "mysql");

    public static SqlDialect named(String name) {
        return named(name, Thread.currentThread().getContextClassLoader());
    }

    public static SqlDialect named(String name, ClassLoader classLoader) {
        String key = name.strip().toLowerCase(Locale.ROOT);
        String wanted = ALIASES.getOrDefault(key, key);
        List<SqlDialect> available = available(classLoader);
        return available.stream()
                .filter(d -> d.name().equals(wanted))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown SQL dialect '" + name + "'; available: "
                        + available.stream().map(SqlDialect::name).sorted().toList()
                        + ". Add the intentflow-" + wanted + " module."));
    }

    public static List<SqlDialect> available(ClassLoader classLoader) {
        return ServiceLoader.load(SqlDialect.class, classLoader).stream().map(ServiceLoader.Provider::get).toList();
    }
}
