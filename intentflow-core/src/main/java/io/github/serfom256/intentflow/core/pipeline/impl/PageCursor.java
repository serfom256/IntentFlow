package io.github.serfom256.intentflow.core.pipeline.impl;

import io.github.serfom256.intentflow.core.ErrorCode;
import io.github.serfom256.intentflow.core.catalog.FieldMapping;
import io.github.serfom256.intentflow.core.catalog.ServiceDescriptor;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import io.github.serfom256.intentflow.core.pipeline.ValidationException;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

record PageCursor(String fingerprint, int offset, @Nullable List<Object> after) {

    private static final String VERSION = "1";

    static String fingerprint(String service, String dsl) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((service + "\n" + dsl).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    String encode() {
        StringBuilder text = new StringBuilder(VERSION).append('|').append(fingerprint).append('|').append(offset);
        if (after != null) {
            text.append('|');
            for (int i = 0; i < after.size(); i++) {
                text.append(i == 0 ? "" : ";").append(value(after.get(i)));
            }
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.toString().getBytes(StandardCharsets.UTF_8));
    }

    static PageCursor decode(String cursor) {
        try {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", -1);
            if (parts.length < 3 || parts.length > 4 || !parts[0].equals(VERSION)) {
                throw invalid();
            }
            int offset = Integer.parseInt(parts[2]);
            if (offset < 0) {
                throw invalid();
            }
            List<Object> after = null;
            if (parts.length == 4) {
                after = new ArrayList<>();
                for (String value : parts[3].split(";", -1)) {
                    after.add(parse(value));
                }
            }
            return new PageCursor(parts[1], offset, after);
        } catch (IllegalArgumentException | DateTimeException e) {
            throw invalid();
        }
    }

    static ValidationException invalid() {
        return new ValidationException(ErrorCode.INVALID_CURSOR, "The cursor does not belong to this question",
                Map.of());
    }

    static @Nullable List<Object> values(Object entity, List<SortKey> keys, ServiceDescriptor service) {
        List<Object> values = new ArrayList<>(keys.size());
        for (SortKey key : keys) {
            String property = service.fields().stream().filter(f -> f.logical().equals(key.field()))
                    .map(FieldMapping::property).findFirst().orElse(null);
            if (property == null || !EntityProperties.has(entity.getClass(), property)) {
                return null;
            }
            Object value = EntityProperties.read(entity, property);
            if (value == null || tag(value) == null) {
                return null;
            }
            values.add(value);
        }
        return values;
    }

    private static @Nullable String tag(Object value) {
        return switch (value) {
            case String s -> "s";
            case Long l -> "l";
            case Integer i -> "l";
            case Short s -> "l";
            case BigDecimal d -> "d";
            case BigInteger i -> "i";
            case Boolean b -> "b";
            case LocalDate d -> "ld";
            case LocalDateTime t -> "lt";
            case OffsetDateTime t -> "ot";
            case ZonedDateTime t -> "ot";
            case Instant i -> "ot";
            case UUID u -> "u";
            default -> null;
        };
    }

    private static String value(Object value) {
        String text = switch (value) {
            case ZonedDateTime t -> t.toOffsetDateTime().toString();
            case Instant i -> i.atOffset(ZoneOffset.UTC).toString();
            default -> value.toString();
        };
        return tag(value) + ":" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static Object parse(String value) {
        int colon = value.indexOf(':');
        if (colon < 0) {
            throw invalid();
        }
        String text = new String(Base64.getUrlDecoder().decode(value.substring(colon + 1)), StandardCharsets.UTF_8);
        return switch (value.substring(0, colon)) {
            case "s" -> text;
            case "l" -> Long.parseLong(text);
            case "d" -> new BigDecimal(text);
            case "i" -> new BigInteger(text);
            case "b" -> Boolean.parseBoolean(text);
            case "ld" -> LocalDate.parse(text);
            case "lt" -> LocalDateTime.parse(text);
            case "ot" -> OffsetDateTime.parse(text);
            case "u" -> UUID.fromString(text);
            default -> throw invalid();
        };
    }
}
