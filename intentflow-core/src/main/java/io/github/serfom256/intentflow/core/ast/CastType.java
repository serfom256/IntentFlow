package io.github.serfom256.intentflow.core.ast;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public enum CastType {
    NUMBER, INTEGER, TEXT, TIMESTAMP, DATE;

    private static final Map<String, CastType> NAMES = Map.ofEntries(Map.entry("number", NUMBER),
            Map.entry("numeric", NUMBER), Map.entry("decimal", NUMBER), Map.entry("integer", INTEGER),
            Map.entry("int", INTEGER), Map.entry("bigint", INTEGER), Map.entry("text", TEXT),
            Map.entry("varchar", TEXT), Map.entry("string", TEXT), Map.entry("timestamp", TIMESTAMP),
            Map.entry("datetime", TIMESTAMP), Map.entry("date", DATE));

    public String keyword() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<CastType> fromName(String name) {
        return Optional.ofNullable(NAMES.get(name.toLowerCase(Locale.ROOT)));
    }
}
