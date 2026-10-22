package io.github.serfom256.intentflow.core;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IntentFlowErrors {

    public static final String BUNDLE = "intentflow/messages";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");
    private static final ResourceBundle.Control NO_FALLBACK = ResourceBundle.Control
            .getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    private IntentFlowErrors() {
    }

    public static String message(IntentFlowException error, Locale locale) {
        String template;
        try {
            template = ResourceBundle.getBundle(BUNDLE, locale, IntentFlowErrors.class.getClassLoader(), NO_FALLBACK)
                    .getString(error.code().value());
        } catch (MissingResourceException e) {
            return error.getMessage();
        }
        return format(template, error);
    }

    static String format(String template, IntentFlowException error) {
        Matcher placeholder = PLACEHOLDER.matcher(template);
        StringBuilder text = new StringBuilder();
        while (placeholder.find()) {
            Object value = error.details().get(placeholder.group(1));
            placeholder.appendReplacement(text, Matcher.quoteReplacement(value == null ? "" : value.toString()));
        }
        placeholder.appendTail(text);
        return text.toString();
    }
}
