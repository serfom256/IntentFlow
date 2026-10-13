package io.github.serfom256.intentflow.core.pipeline.impl;

import io.github.serfom256.intentflow.core.api.UserText;
import io.github.serfom256.intentflow.core.api.factories.UserTexts;
import io.github.serfom256.intentflow.core.pipeline.TextNormalizer;

import java.util.regex.Pattern;

public final class DefaultTextNormalizer implements TextNormalizer {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    @Override
    public UserText normalize(UserText input) {
        String collapsed = WHITESPACE.matcher(input.value().trim()).replaceAll(" ");
        return UserTexts.of(collapsed);
    }
}
