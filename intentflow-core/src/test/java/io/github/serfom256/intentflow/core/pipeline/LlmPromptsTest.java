package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.catalog.annotation.IntentField;
import io.github.serfom256.intentflow.core.catalog.annotation.IntentService;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceCatalogs;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class LlmPromptsTest {

    enum Genre {
        FANTASY, HISTORY
    }

    @IntentService(value = "books", uri = "/books/{{id}}", description = "Books in stock")
    record Book(@IntentField long id, @IntentField(description = "as printed") String title,
            @IntentField Genre genre, String supplier) {
    }

    @Test
    void theSystemPromptListsTheCatalogAndToday() {
        String prompt = LlmPrompts.system(ServiceCatalogs.fromAnnotated(Book.class), LocalDate.of(2026, 10, 21));

        assertThat(prompt).contains("Today is 2026-10-21.")
                .contains("- books: Books in stock")
                .contains("    title (text) - as printed")
                .contains("    genre (text, one of: FANTASY, HISTORY)")
                .doesNotContain("supplier");
    }

    @Test
    void answersLoseThinkingAndCodeFences() {
        assertThat(LlmPrompts.answer("<think>which field?</think>\n```dsl\nbooks | take 1\n```"))
                .isEqualTo("books | take 1");
        assertThat(LlmPrompts.answer("  books  ")).isEqualTo("books");
    }

    @Test
    void theRepairInstructionCarriesTheError() {
        assertThat(LlmPrompts.repair("field 'author' is not declared"))
                .startsWith("That query was rejected: field 'author' is not declared.");
    }
}
