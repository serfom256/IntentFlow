package io.github.serfom256.intentflow.core.pipeline.impl;

import io.github.serfom256.intentflow.core.api.MicroFrontendUri;
import io.github.serfom256.intentflow.core.api.factories.MicroFrontendUris;
import io.github.serfom256.intentflow.core.catalog.FieldMapping;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.pipeline.UriBuildException;
import io.github.serfom256.intentflow.core.pipeline.UriBuilder;
import io.github.serfom256.intentflow.core.pipeline.ValidatedQuery;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

public final class DefaultUriBuilder implements UriBuilder {

    @Override
    public MicroFrontendUri build(ValidatedQuery query, List<?> items) {
        ServiceDescriptor service = query.service();
        String template = service.uriTemplate().value();
        Map<String, FieldMapping> fields = new HashMap<>();
        for (FieldMapping fm : service.fields()) {
            fields.put(fm.logical().value(), fm);
        }

        Matcher names = UriPlaceholders.PLACEHOLDER.matcher(template);
        while (names.find()) {
            if (!fields.containsKey(names.group(1))) {
                throw new UriBuildException("URI template references unknown field '" + names.group(1) + "'");
            }
        }
        if (items.isEmpty()) {
            return MicroFrontendUris.of(UriPlaceholders.withoutPlaceholders(template));
        }

        Object first = items.getFirst();
        Matcher matcher = UriPlaceholders.PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String property = propertyOf(fields.get(matcher.group(1)));
            if (!EntityProperties.has(first.getClass(), property)) {
                throw new UriBuildException(first.getClass().getName() + " has no property '" + property
                        + "' for URI placeholder {{" + matcher.group(1) + "}}");
            }
            Object value = EntityProperties.read(first, property);
            matcher.appendReplacement(out, Matcher.quoteReplacement(encodePathSegment(render(value))));
        }
        matcher.appendTail(out);
        return MicroFrontendUris.of(out.toString());
    }

    private static String propertyOf(FieldMapping fm) {
        if (fm.property() != null) {
            return fm.property();
        }
        String[] parts = fm.logical().value().split("_");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
            }
        }
        return sb.toString();
    }

    private static String render(@Nullable Object value) {
        return switch (value) {
            case null -> "";
            case BigDecimal bd -> bd.toPlainString();
            case Enum<?> e -> e.name();
            default -> value.toString();
        };
    }

    private static String encodePathSegment(String raw) {
        return URLEncoder.encode(raw, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
