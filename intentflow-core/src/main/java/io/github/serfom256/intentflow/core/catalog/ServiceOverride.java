package io.github.serfom256.intentflow.core.catalog;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public record ServiceOverride(@Nullable String description, @Nullable String uri,
        Map<String, String> fieldDescriptions) {

    public static final int MAX_DESCRIPTION_LENGTH = 1000;
    public static final int MAX_URI_LENGTH = 500;

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    public static final ServiceOverride NONE = new ServiceOverride(null, null, Map.of());

    public ServiceOverride {
        description = blankToNull(description);
        uri = blankToNull(uri);
        Map<String, String> fields = new LinkedHashMap<>();
        fieldDescriptions.forEach((field, text) -> {
            String value = blankToNull(text);
            if (value != null) {
                fields.put(field, value);
            }
        });
        fieldDescriptions = Map.copyOf(fields);
    }

    public boolean isEmpty() {
        return description == null && uri == null && fieldDescriptions.isEmpty();
    }

    public List<String> problems(ServiceDescriptor service) {
        Set<String> fields = service.fields().stream().filter(FieldMapping::queryable)
                .map(f -> f.logical().value()).collect(Collectors.toSet());
        Set<String> all = service.fields().stream().map(f -> f.logical().value()).collect(Collectors.toSet());
        List<String> problems = new ArrayList<>();
        text("description", description, MAX_DESCRIPTION_LENGTH, problems);
        fieldDescriptions.forEach((field, text) -> {
            if (!fields.contains(field)) {
                problems.add("'" + field + "' is not a queryable field of " + service.name().value());
            }
            text("description of '" + field + "'", text, MAX_DESCRIPTION_LENGTH, problems);
        });
        if (uri != null) {
            text("uri", uri, MAX_URI_LENGTH, problems);
            if (!uri.startsWith("/") && !uri.contains("://")) {
                problems.add("uri must start with '/' or be an absolute URL");
            }
            Matcher matcher = PLACEHOLDER.matcher(uri);
            while (matcher.find()) {
                if (!all.contains(matcher.group(1))) {
                    problems.add("uri placeholder {{" + matcher.group(1) + "}} is not a field of "
                            + service.name().value());
                }
            }
        }
        return problems;
    }

    private static void text(String what, @Nullable String value, int max, List<String> problems) {
        if (value == null) {
            return;
        }
        if (value.length() > max) {
            problems.add(what + " is longer than " + max + " characters");
        }
        if (value.chars().anyMatch(Character::isISOControl)) {
            problems.add(what + " must be a single line without control characters");
        }
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
