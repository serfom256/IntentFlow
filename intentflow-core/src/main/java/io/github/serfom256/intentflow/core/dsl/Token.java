package io.github.serfom256.intentflow.core.dsl;

public sealed interface Token
        permits IdentifierToken, StringToken, NumberToken,
        KeywordToken, OperatorToken, PunctuationToken, EofToken {

    Position position();
}
