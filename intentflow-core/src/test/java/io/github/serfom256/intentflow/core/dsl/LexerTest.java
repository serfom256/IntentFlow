package io.github.serfom256.intentflow.core.dsl;

import io.github.serfom256.intentflow.core.ast.factories.DslSources;
import io.github.serfom256.intentflow.core.dsl.factories.Lexers;
import io.github.serfom256.intentflow.core.dsl.factories.Positions;
import io.github.serfom256.intentflow.core.pipeline.DslParseException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LexerTest {

    private final Lexer lexer = Lexers.create();

    private List<Token> lex(String dsl) {
        return lexer.tokenize(DslSources.of(dsl));
    }

    @Test
    void identifierIsRecognised() {
        List<Token> t = lex("author");
        assertThat(t).hasSize(2);
        assertThat(t.get(0)).isInstanceOfSatisfying(IdentifierToken.class,
                id -> assertThat(id.name()).isEqualTo("author"));
        assertThat(t.get(1)).isInstanceOf(EofToken.class);
    }

    @Test
    void underscorePrefixedIdentifierIsAllowed() {
        List<Token> t = lex("_internalField");
        assertThat(t.get(0)).isInstanceOfSatisfying(IdentifierToken.class,
                id -> assertThat(id.name()).isEqualTo("_internalField"));
    }

    @Test
    void keywordsAreReturnedAsKeywordTokens() {
        List<Token> t = lex("filter and or not in null asc desc true false unsupported sort skip take");
        for (int i = 0; i < t.size() - 1; i++) {
            assertThat(t.get(i)).isInstanceOf(KeywordToken.class);
        }
    }

    @Test
    void keywordsAreCaseInsensitive() {
        assertThat(lex("FILTER").get(0)).isInstanceOfSatisfying(KeywordToken.class,
                kw -> assertThat(kw.keyword()).isEqualTo(Keyword.FILTER));
        assertThat(lex("Filter").get(0)).isInstanceOfSatisfying(KeywordToken.class,
                kw -> assertThat(kw.keyword()).isEqualTo(Keyword.FILTER));
    }

    @Test
    void identifiersAreCaseSensitive() {
        List<Token> t = lex("Author");
        assertThat(t.get(0)).isInstanceOfSatisfying(IdentifierToken.class,
                id -> assertThat(id.name()).isEqualTo("Author"));
    }

    @Test
    void integerLiteral() {
        List<Token> t = lex("42");
        assertThat(t.get(0)).isInstanceOfSatisfying(NumberToken.class,
                n -> assertThat(n.value()).isEqualByComparingTo(new BigDecimal("42")));
    }

    @Test
    void decimalLiteral() {
        List<Token> t = lex("3.14");
        assertThat(t.get(0)).isInstanceOfSatisfying(NumberToken.class,
                n -> assertThat(n.value()).isEqualByComparingTo(new BigDecimal("3.14")));
    }

    @Test
    void trailingDotIsRejected() {
        assertThatThrownBy(() -> lex("3."))
                .isInstanceOf(DslParseException.class)
                .hasMessageContaining("digit after '.'");
    }

    @Test
    void simpleString() {
        List<Token> t = lex("\"John Doe\"");
        assertThat(t.get(0)).isInstanceOfSatisfying(StringToken.class,
                s -> assertThat(s.value()).isEqualTo("John Doe"));
    }

    @Test
    void stringWithQuoteEscape() {
        List<Token> t = lex("\"the \\\"king\\\"\"");
        assertThat(t.get(0)).isInstanceOfSatisfying(StringToken.class,
                s -> assertThat(s.value()).isEqualTo("the \"king\""));
    }

    @Test
    void stringWithBackslashEscape() {
        List<Token> t = lex("\"a\\\\b\"");
        assertThat(t.get(0)).isInstanceOfSatisfying(StringToken.class,
                s -> assertThat(s.value()).isEqualTo("a\\b"));
    }

    @Test
    void stringWithNewlineEscape() {
        List<Token> t = lex("\"line1\\nline2\"");
        assertThat(t.get(0)).isInstanceOfSatisfying(StringToken.class,
                s -> assertThat(s.value()).isEqualTo("line1\nline2"));
    }

    @Test
    void stringWithTabEscape() {
        List<Token> t = lex("\"col1\\tcol2\"");
        assertThat(t.get(0)).isInstanceOfSatisfying(StringToken.class,
                s -> assertThat(s.value()).isEqualTo("col1\tcol2"));
    }

    @Test
    void unterminatedStringIsRejected() {
        assertThatThrownBy(() -> lex("\"unterminated"))
                .isInstanceOf(DslParseException.class)
                .hasMessageContaining("unterminated string");
    }

    @Test
    void invalidEscapeIsRejected() {
        assertThatThrownBy(() -> lex("\"bad\\xescape\""))
                .isInstanceOf(DslParseException.class)
                .hasMessageContaining("invalid escape");
    }

    @Test
    void rawNewlineInStringIsRejected() {
        assertThatThrownBy(() -> lex("\"line1\nline2\""))
                .isInstanceOf(DslParseException.class)
                .hasMessageContaining("unterminated string");
    }

    @Test
    void allOperatorsAreRecognised() {
        assertOp("==", Operator.EQ);
        assertOp("!=", Operator.NE);
        assertOp("<", Operator.LT);
        assertOp("<=", Operator.LE);
        assertOp(">", Operator.GT);
        assertOp(">=", Operator.GE);
        assertOp("~", Operator.CONTAINS);
    }

    private void assertOp(String src, Operator expected) {
        assertThat(lex(src).get(0)).isInstanceOfSatisfying(OperatorToken.class,
                op -> assertThat(op.operator()).isEqualTo(expected));
    }

    @Test
    void multiCharOperatorsBeatSingleChar() {
        List<Token> t = lex(">=");
        assertThat(t).hasSize(2);
        assertThat(t.get(0)).isInstanceOfSatisfying(OperatorToken.class,
                op -> assertThat(op.operator()).isEqualTo(Operator.GE));
    }

    @Test
    void operatorsWithoutWhitespace() {
        List<Token> t = lex("a==b");
        assertThat(t).hasSize(4);
        assertThat(t.get(0)).isInstanceOf(IdentifierToken.class);
        assertThat(t.get(1)).isInstanceOf(OperatorToken.class);
        assertThat(t.get(2)).isInstanceOf(IdentifierToken.class);
    }

    @Test
    void bareEqualsIsRejected() {
        assertThatThrownBy(() -> lex("a = b"))
                .isInstanceOf(DslParseException.class)
                .hasMessageContaining("unexpected character '='");
    }

    @Test
    void bareBangIsRejected() {
        assertThatThrownBy(() -> lex("!a"))
                .isInstanceOf(DslParseException.class)
                .hasMessageContaining("unexpected character '!'");
    }

    @Test
    void allPunctuationIsRecognised() {
        assertPunct("|", Punctuation.PIPE);
        assertPunct("(", Punctuation.LPAREN);
        assertPunct(")", Punctuation.RPAREN);
        assertPunct(",", Punctuation.COMMA);
    }

    private void assertPunct(String src, Punctuation expected) {
        assertThat(lex(src).get(0)).isInstanceOfSatisfying(PunctuationToken.class,
                p -> assertThat(p.punctuation()).isEqualTo(expected));
    }

    @Test
    void whitespaceIsSkipped() {
        List<Token> t = lex("   author   ==   \"Doe\"   ");
        assertThat(t).hasSize(4);
        assertThat(t.get(0)).isInstanceOf(IdentifierToken.class);
        assertThat(t.get(1)).isInstanceOf(OperatorToken.class);
        assertThat(t.get(2)).isInstanceOf(StringToken.class);
        assertThat(t.get(3)).isInstanceOf(EofToken.class);
    }

    @Test
    void fullPipelineQuery() {
        List<Token> t = lex(
                "books | filter author == \"Doe\" and year >= 2000 | sort year desc | take 20");
        assertThat(t).hasSize(18);
        assertThat(t.get(0)).isInstanceOfSatisfying(IdentifierToken.class,
                id -> assertThat(id.name()).isEqualTo("books"));
        assertThat(t.get(2)).isInstanceOfSatisfying(KeywordToken.class,
                kw -> assertThat(kw.keyword()).isEqualTo(Keyword.FILTER));
        assertThat(t.get(t.size() - 1)).isInstanceOf(EofToken.class);
    }

    @Test
    void inListTokensCorrectly() {
        List<Token> t = lex("status in (\"a\", \"b\")");
        assertThat(t.get(0)).isInstanceOfSatisfying(IdentifierToken.class,
                id -> assertThat(id.name()).isEqualTo("status"));
        assertThat(t.get(1)).isInstanceOfSatisfying(KeywordToken.class,
                kw -> assertThat(kw.keyword()).isEqualTo(Keyword.IN));
        assertThat(t.get(2)).isInstanceOfSatisfying(PunctuationToken.class,
                p -> assertThat(p.punctuation()).isEqualTo(Punctuation.LPAREN));
        assertThat(t.get(3)).isInstanceOfSatisfying(StringToken.class,
                s -> assertThat(s.value()).isEqualTo("a"));
        assertThat(t.get(4)).isInstanceOfSatisfying(PunctuationToken.class,
                p -> assertThat(p.punctuation()).isEqualTo(Punctuation.COMMA));
    }

    @Test
    void nullKeywordIsRecognised() {
        List<Token> t = lex("deletedAt == null");
        assertThat(t.get(2)).isInstanceOfSatisfying(KeywordToken.class,
                kw -> assertThat(kw.keyword()).isEqualTo(Keyword.NULL));
    }

    @Test
    void positionsTrackSourceOffsets() {
        List<Token> t = lex("a == 42");
        assertThat(t.get(0).position().offset()).isZero();
        assertThat(t.get(1).position().offset()).isEqualTo(2);
        assertThat(t.get(2).position().offset()).isEqualTo(5);
        assertThat(t.get(3).position().offset()).isEqualTo(7);
    }

    @Test
    void positionsStartAtLineOneColumnOne() {
        List<Token> t = lex("a");
        assertThat(t.get(0).position().line()).isEqualTo(1);
        assertThat(t.get(0).position().column()).isEqualTo(1);
    }

    @Test
    void columnAdvancesWithinSingleLine() {
        List<Token> t = lex("a == 42");
        assertThat(t.get(0).position().column()).isEqualTo(1);
        assertThat(t.get(1).position().column()).isEqualTo(3);
        assertThat(t.get(2).position().column()).isEqualTo(6);
    }

    @Test
    void newlineAdvancesLineAndResetsColumn() {
        List<Token> t = lex("a\nb");
        assertThat(t.get(0).position().line()).isEqualTo(1);
        assertThat(t.get(0).position().column()).isEqualTo(1);
        assertThat(t.get(1).position().line()).isEqualTo(2);
        assertThat(t.get(1).position().column()).isEqualTo(1);
    }

    @Test
    void multipleNewlinesAccumulate() {
        List<Token> t = lex("a\n\n\nb");
        assertThat(t.get(1).position().line()).isEqualTo(4);
    }

    @Test
    void errorMessageMentionsLineAndColumn() {
        assertThatThrownBy(() -> lex("a\n!b"))
                .isInstanceOf(DslParseException.class)
                .hasMessageContaining("line 2")
                .hasMessageContaining("column 1");
    }

    @Test
    void emptyDslSourceRejectedAtConstruction() {
        assertThatThrownBy(() -> DslSources.of(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void whitespaceOnlyDslSourceRejectedAtConstruction() {
        assertThatThrownBy(() -> DslSources.of("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void singleTokenStillProducesEof() {
        List<Token> t = lex("books");
        assertThat(t).hasSize(2);
        assertThat(t.get(1)).isInstanceOf(EofToken.class);
    }

    @Test
    void resultIsImmutable() {
        List<Token> t = lex("a");
        assertThatThrownBy(() -> t.add(new EofToken(Positions.of(0, 1, 1))))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void negativeNumbersAreSingleTokens() {
        List<Token> t = lex("balance < -100.5");
        assertThat(t.get(2)).isInstanceOfSatisfying(NumberToken.class,
                n -> assertThat(n.value()).isEqualByComparingTo(new BigDecimal("-100.5")));
        assertThat(t).hasSize(4);
    }

    @Test
    void minusAfterAValueIsSubtractionAndOtherwiseANegativeNumber() {
        assertThat(lex("x - 5")).extracting(t -> t.getClass().getSimpleName())
                .containsExactly("IdentifierToken", "PunctuationToken", "NumberToken", "EofToken");
        assertThat(lex("x == -5")).extracting(t -> t.getClass().getSimpleName())
                .containsExactly("IdentifierToken", "OperatorToken", "NumberToken", "EofToken");
    }
}
