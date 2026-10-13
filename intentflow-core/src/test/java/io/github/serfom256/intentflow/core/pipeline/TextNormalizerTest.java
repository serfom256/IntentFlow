package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.api.UserText;
import io.github.serfom256.intentflow.core.api.factories.UserTexts;
import io.github.serfom256.intentflow.core.pipeline.factories.TextNormalizers;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextNormalizerTest {

    private final TextNormalizer normalizer = TextNormalizers.create();

    @Test
    void trimsLeadingAndTrailingWhitespace() {
        assertThat(normalizer.normalize(UserTexts.of("  hello  ")).value())
                .isEqualTo("hello");
    }

    @Test
    void collapsesRunsOfSpaces() {
        assertThat(normalizer.normalize(UserTexts.of("hello    world")).value())
                .isEqualTo("hello world");
    }

    @Test
    void collapsesTabsAndNewlines() {
        assertThat(normalizer.normalize(UserTexts.of("hello\t\nworld")).value())
                .isEqualTo("hello world");
    }

    @Test
    void preservesCaseOfProperNouns() {
        assertThat(normalizer.normalize(UserTexts.of("Find John Doe books")).value())
                .isEqualTo("Find John Doe books");
    }

    @Test
    void mixedWhitespaceFullyNormalised() {
        assertThat(normalizer.normalize(UserTexts.of("  find\t  John\n\nDoe  ")).value())
                .isEqualTo("find John Doe");
    }

    @Test
    void isIdempotent() {
        UserText once = normalizer.normalize(UserTexts.of("  hello   world  "));
        UserText twice = normalizer.normalize(once);
        assertThat(twice.value()).isEqualTo(once.value());
    }

    @Test
    void inputWithNoWhitespaceChangesNothing() {
        assertThat(normalizer.normalize(UserTexts.of("xyz")).value()).isEqualTo("xyz");
    }
}
