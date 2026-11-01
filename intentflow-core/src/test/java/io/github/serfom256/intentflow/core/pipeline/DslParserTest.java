package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ast.AstQuery;
import io.github.serfom256.intentflow.core.ast.Compare;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.pipeline.factories.DslParsers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DslParserTest {

    private final DslParser dslParser = DslParsers.create();

    @Test
    void parsesEndToEnd() {
        AstQuery q = dslParser.parse(DslSources.of("books | filter author == \"Doe\" | sort id desc | take 5"));

        assertThat(q.service().value()).isEqualTo("books");
        assertThat(q.filter().orElseThrow()).isInstanceOf(Compare.class);
        assertThat(q.sort()).hasSize(1);
        assertThat(q.take().orElseThrow().value()).isEqualTo(5);
    }

    @Test
    void propagatesLexErrors() {
        assertThatThrownBy(() -> dslParser.parse(DslSources.of("books | filter a = b")))
                .isInstanceOf(DslParseException.class);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
            "unsupported \"no publisher field\"                                   | no publisher field",
            "  Unsupported 'no publisher field'                                     | no publisher field",
            "unsupported 'group' only works on fields of 'orders', not 'customer.country' | 'group' only works on fields of 'orders', not 'customer.country'",
            "unsupported: books have no publisher                                   | : books have no publisher",
            "unsupported                                                            | no reason given" })
    void anyAnswerStartingWithUnsupportedDeclinesWithItsReason(String answer, String reason) {
        assertThatThrownBy(() -> dslParser.parse(DslSources.of(answer))).isInstanceOfSatisfying(
                UnsupportedRequestException.class, e -> assertThat(e.reason()).isEqualTo(reason));
    }

    @Test
    void aServiceNamedLikeTheKeywordPrefixIsStillAQuery() {
        assertThat(dslParser.parse(DslSources.of("unsupported_items | take 1")).service().value())
                .isEqualTo("unsupported_items");
    }

    @Test
    void propagatesParseErrors() {
        assertThatThrownBy(() -> dslParser.parse(DslSources.of("books | filter")))
                .isInstanceOf(DslParseException.class);
    }
}
