package io.github.serfom256.intentflow.core.pipeline.impl;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class Timestamps {

    static boolean isValid(String text) {
        String s = text.strip().replace(' ', 'T');
        return parses(() -> LocalDate.parse(s))
                || parses(() -> LocalDateTime.parse(s))
                || parses(() -> OffsetDateTime.parse(s));
    }

    static boolean isDateOnly(String text) {
        return parses(() -> LocalDate.parse(text.strip()));
    }

    static String nextDay(String date) {
        return LocalDate.parse(date.strip()).plusDays(1).toString();
    }

    private static boolean parses(Runnable parse) {
        try {
            parse.run();
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
