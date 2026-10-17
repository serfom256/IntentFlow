package io.github.serfom256.intentflow.core.pipeline.impl;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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

    static LocalDateTime local(String text, ZoneId zone) {
        String s = text.strip().replace(' ', 'T');
        try {
            return LocalDate.parse(s).atStartOfDay();
        } catch (DateTimeParseException notDate) {
            try {
                return LocalDateTime.parse(s);
            } catch (DateTimeParseException notLocal) {
                return OffsetDateTime.parse(s).atZoneSameInstant(zone).toLocalDateTime();
            }
        }
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
