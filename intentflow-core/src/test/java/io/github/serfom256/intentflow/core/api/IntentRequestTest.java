package io.github.serfom256.intentflow.core.api;

import io.github.serfom256.intentflow.core.api.factories.UserTexts;
import io.github.serfom256.intentflow.core.api.factories.MicroFrontendUris;
import io.github.serfom256.intentflow.core.api.factories.IntentRequests;
import io.github.serfom256.intentflow.core.api.factories.IntentResponses;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IntentRequestTest {

    @Test
    void carriesUserText() {
        IntentRequest request = IntentRequests.of(UserTexts.of("find books"));
        assertThat(request.text().value()).isEqualTo("find books");
    }
}
