package io.github.serfom256.intentflow.core.sql;

import java.util.Arrays;
import java.util.Locale;

public enum Optimization {
    NOT_NULL_ORDERING,
    EXACT_VALUES,
    DATE_RANGES,
    TO_ONE_JOIN,
    KEY_PROJECTION;

    public String id() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static Optimization byId(String id) {
        return Arrays.stream(values()).filter(o -> o.id().equalsIgnoreCase(id.strip())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown optimization '" + id + "', expected one of "
                        + Arrays.stream(values()).map(Optimization::id).toList()));
    }
}
