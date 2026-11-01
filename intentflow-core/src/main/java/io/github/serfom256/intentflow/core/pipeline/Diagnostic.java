package io.github.serfom256.intentflow.core.pipeline;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record Diagnostic(String message, Optional<Mark> mark, List<String> notes, Optional<String> help) {

    public static final String MARKED = "{marked}";

    public record Mark(int start, int end, Optional<String> text, String label) {

        public static Mark at(int start, int end, String label) {
            return new Mark(start, end, Optional.empty(), label);
        }

        public static Mark of(String text, String label) {
            return new Mark(-1, -1, Optional.of(text), label);
        }
    }

    public Diagnostic {
        notes = List.copyOf(notes);
    }

    public static Diagnostic of(String message) {
        return new Diagnostic(message, Optional.empty(), List.of(), Optional.empty());
    }

    public Diagnostic at(int start, int end, String label) {
        return new Diagnostic(message, Optional.of(Mark.at(start, end, label)), notes, help);
    }

    public Diagnostic near(String text, String label) {
        return new Diagnostic(message, Optional.of(Mark.of(text, label)), notes, help);
    }

    public Diagnostic note(String note) {
        List<String> more = new ArrayList<>(notes);
        more.add(note);
        return new Diagnostic(message, mark, more, help);
    }

    public Diagnostic help(String help) {
        return new Diagnostic(message, mark, notes, Optional.of(help));
    }

    public String render(String dsl) {
        int @Nullable [] span = mark.map(m -> span(m, dsl)).orElse(null);
        int start = span == null ? 0 : span[0];
        int lineStart = dsl.lastIndexOf('\n', Math.max(0, start - 1)) + 1;
        int lineEnd = dsl.indexOf('\n', lineStart);
        if (lineEnd < 0) {
            lineEnd = dsl.length();
        }
        String number = Integer.toString(dsl.substring(0, lineStart).split("\n", -1).length);
        String gutter = " ".repeat(number.length());
        StringBuilder out = new StringBuilder("error: ").append(message).append('\n');
        out.append(gutter).append(" |\n");
        out.append(number).append(" | ").append(dsl, lineStart, lineEnd).append('\n');
        String marked = "";
        if (span != null) {
            int end = Math.min(span[1], lineEnd);
            marked = dsl.substring(start, end);
            out.append(gutter).append(" | ").append(" ".repeat(start - lineStart)).append("^".repeat(end - start));
            String label = mark.get().label();
            out.append(label.isEmpty() ? "" : " " + label).append('\n');
        }
        for (String note : notes) {
            out.append(gutter).append(" = note: ").append(note).append('\n');
        }
        String fill = marked;
        help.ifPresent(h -> out.append(gutter).append(" = help: ")
                .append(h.replace(MARKED, fill).replace("\n", "\n" + gutter + "         ")).append('\n'));
        return out.toString().stripTrailing();
    }

    private static int @Nullable [] span(Mark mark, String dsl) {
        int start = mark.start();
        int end = mark.end();
        if (mark.text().isPresent()) {
            start = dsl.indexOf(mark.text().get());
            if (start < 0) {
                return null;
            }
            end = start + mark.text().get().length();
        }
        if (start < 0 || start >= dsl.length()) {
            return null;
        }
        end = Math.min(Math.max(end, start + 1), dsl.length());
        while (end > start + 1 && Character.isWhitespace(dsl.charAt(end - 1))) {
            end--;
        }
        return new int[] { start, end };
    }
}
