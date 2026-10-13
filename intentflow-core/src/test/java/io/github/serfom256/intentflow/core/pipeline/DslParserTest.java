package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ast.AstQuery;
import io.github.serfom256.intentflow.core.ast.Compare;
import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.pipeline.factories.DslParsers;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DslParserTest {

    private final DslParser dslParser = DslParsers.create();

    @Test
    void parsesEndToEnd() {
        AstQuery q = dslParser.parse(DslSources.of(
                "books | filter author == \"Doe\" | sort id desc | take 5"));

        assertThat(q.service().value()).isEqualTo("books");
        assertThat(q.filter()).isInstanceOf(Compare.class);
        assertThat(q.sort()).hasSize(1);
        assertThat(q.take().value()).isEqualTo(5);
    }

    @Test
    void propagatesLexErrors() {
        assertThatThrownBy(() -> dslParser.parse(DslSources.of("books | filter a = b")))
                .isInstanceOf(DslParseException.class);
    }

    @Test
    void propagatesParseErrors() {
        assertThatThrownBy(() -> dslParser.parse(DslSources.of("books | filter")))
                .isInstanceOf(DslParseException.class);
    }
}
