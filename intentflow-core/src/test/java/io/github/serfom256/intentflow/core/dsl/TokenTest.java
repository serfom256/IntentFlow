package io.github.serfom256.intentflow.core.dsl;

import io.github.serfom256.intentflow.core.dsl.factories.Lexers;
import io.github.serfom256.intentflow.core.dsl.factories.Positions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TokenTest {

    private static final Position POS = Positions.of(0, 1, 1);

    @Test
    void sealedHierarchyIsExhaustiveInSwitch() {
        Token[] tokens = {
                new IdentifierToken("author", POS),
                new StringToken("hello", POS),
                new NumberToken(BigDecimal.ONE, POS),
                new KeywordToken(Keyword.FILTER, POS),
                new OperatorToken(Operator.EQ, POS),
                new PunctuationToken(Punctuation.PIPE, POS),
                new EofToken(POS),
        };
        List<String> labels = new ArrayList<>();
        for (Token t : tokens) {
            String label = switch (t) {
                case IdentifierToken id -> "ident:" + id.name();
                case StringToken str -> "str:" + str.value();
                case NumberToken num -> "num:" + num.value();
                case KeywordToken kw -> "kw:" + kw.keyword();
                case OperatorToken op -> "op:" + op.operator();
                case PunctuationToken p -> "punct:" + p.punctuation();
                case EofToken eof -> "eof";
            };
            labels.add(label);
        }
        assertThat(labels).hasSize(7);
    }
}
