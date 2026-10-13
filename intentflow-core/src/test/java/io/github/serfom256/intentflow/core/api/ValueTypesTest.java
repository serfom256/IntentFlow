package io.github.serfom256.intentflow.core.api;

import io.github.serfom256.intentflow.core.api.factories.UserTexts;
import io.github.serfom256.intentflow.core.api.factories.MicroFrontendUris;
import io.github.serfom256.intentflow.core.api.factories.IntentRequests;
import io.github.serfom256.intentflow.core.api.factories.IntentResponses;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValueTypesTest {

    @Test
    void userTextHoldsValue() {
        assertThat(UserTexts.of("find books").value()).isEqualTo("find books");
    }

    @Test
    void userTextRejectsBlank() {
        assertThatThrownBy(() -> UserTexts.of("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-blank");
    }

    @Test
    void microFrontendUriHoldsValue() {
        assertThat(MicroFrontendUris.of("/books?ids=1").value()).isEqualTo("/books?ids=1");
    }

    @Test
    void microFrontendUriRejectsBlank() {
        assertThatThrownBy(() -> MicroFrontendUris.of(""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
