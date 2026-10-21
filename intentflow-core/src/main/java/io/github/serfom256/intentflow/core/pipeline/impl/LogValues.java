package io.github.serfom256.intentflow.core.pipeline.impl;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.regex.Pattern;

final class LogValues {

    private static final Pattern STRING = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");
    private static final Pattern NUMBER = Pattern.compile("(?<![\\w.])-?\\d+(?:\\.\\d+)?(?![\\w.])");

    private LogValues() {
    }

    static Object text(boolean shown, String text) {
        return shown ? text : "<" + text.length() + " chars>";
    }

    static Object dsl(boolean shown, String dsl) {
        return shown ? dsl : NUMBER.matcher(STRING.matcher(dsl).replaceAll("?")).replaceAll("?");
    }

    static Object values(boolean shown, List<@Nullable Object> values) {
        return shown ? values : "<" + values.size() + " values>";
    }
}
