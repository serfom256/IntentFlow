package io.github.serfom256.intentflow.core.pipeline.impl;

import io.github.serfom256.intentflow.core.pipeline.Diagnostic;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class Suggestions {

    private Suggestions() {
    }

    static Diagnostic unknownField(String shown, String key, Collection<String> fields) {
        List<String> sorted = fields.stream().sorted().toList();
        Diagnostic diagnostic = Diagnostic.of("unknown field '" + shown + "'").near(shown, "not a field here");
        String prefix = shown.substring(0, shown.length() - key.length());
        String listed = "the fields are " + String.join(", ", sorted);
        return closest(key, sorted).map(c -> diagnostic.help("did you mean '" + prefix + c + "'? " + listed))
                .orElseGet(() -> diagnostic.help(listed));
    }

    static Diagnostic relatedInExpression(String field) {
        return Diagnostic.of("related field '" + field + "' cannot be used inside a function or arithmetic")
                .near(field, "a related field").note("functions and arithmetic work on the service's own fields")
                .help("compare the related field directly, without a function: " + field + " >= \"2026-01-01\" and "
                        + field + " < \"2027-01-01\" for a year, " + field + " > 100 for a number");
    }

    private static Optional<String> closest(String word, List<String> candidates) {
        String lower = word.toLowerCase(Locale.ROOT);
        return candidates.stream()
                .filter(c -> distance(lower, c.toLowerCase(Locale.ROOT)) <= limit(word)
                        || c.toLowerCase(Locale.ROOT).contains(lower) || lower.contains(c.toLowerCase(Locale.ROOT)))
                .min(Comparator.comparingInt(c -> distance(lower, c.toLowerCase(Locale.ROOT))));
    }

    private static int limit(String word) {
        return word.length() <= 4 ? 1 : 2;
    }

    private static int distance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
