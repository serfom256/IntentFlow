package io.github.serfom256.intentflow.core.pipeline;

public record SqlCapabilities(boolean windowFunctions, boolean booleans, boolean sortBySubquery,
        boolean groupByAlias, boolean enforcedKeys, int maxInListSize, int maxParameters) {

    public static final SqlCapabilities STANDARD = new SqlCapabilities(true, true, true, true, true,
            Integer.MAX_VALUE, 65_535);

    public SqlCapabilities {
        if (maxInListSize < 1 || maxParameters < 1) {
            throw new IllegalArgumentException("limits must be positive");
        }
    }
}
