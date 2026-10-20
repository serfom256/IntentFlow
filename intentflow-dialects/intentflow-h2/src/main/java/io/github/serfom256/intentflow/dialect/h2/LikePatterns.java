package io.github.serfom256.intentflow.dialect.h2;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class LikePatterns {

    static String containing(String text, char escape) {
        StringBuilder sb = new StringBuilder(text.length() + 2).append('%');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == escape || c == '%' || c == '_') {
                sb.append(escape);
            }
            sb.append(c);
        }
        return sb.append('%').toString();
    }
}
