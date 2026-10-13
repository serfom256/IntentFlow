package io.github.serfom256.intentflow.core.pipeline.impl;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class UriPlaceholders {

    static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    static String withoutPlaceholders(String template) {
        int q = template.indexOf('?');
        String path = q < 0 ? template : template.substring(0, q);
        String query = q < 0 ? "" : template.substring(q + 1);

        StringBuilder out = new StringBuilder();
        String[] segments = path.split("/", -1);
        for (int i = 0; i < segments.length; i++) {
            if (PLACEHOLDER.matcher(segments[i]).find()) {
                break;
            }
            if (i > 0) out.append('/');
            out.append(segments[i]);
        }
        if (out.isEmpty() && path.startsWith("/")) {
            out.append('/');
        }

        List<String> params = new ArrayList<>();
        for (String param : query.split("&")) {
            if (!param.isEmpty() && !PLACEHOLDER.matcher(param).find()) {
                params.add(param);
            }
        }
        if (!params.isEmpty()) {
            out.append('?').append(String.join("&", params));
        }
        return out.toString();
    }
}
