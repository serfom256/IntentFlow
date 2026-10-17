package io.github.serfom256.intentflow.core.dsl.impl;

import io.github.serfom256.intentflow.core.ast.DslSource;
import io.github.serfom256.intentflow.core.dsl.EofToken;
import io.github.serfom256.intentflow.core.dsl.IdentifierToken;
import io.github.serfom256.intentflow.core.dsl.Keyword;
import io.github.serfom256.intentflow.core.dsl.KeywordToken;
import io.github.serfom256.intentflow.core.dsl.Lexer;
import io.github.serfom256.intentflow.core.dsl.NumberToken;
import io.github.serfom256.intentflow.core.dsl.Operator;
import io.github.serfom256.intentflow.core.dsl.OperatorToken;
import io.github.serfom256.intentflow.core.dsl.Position;
import io.github.serfom256.intentflow.core.dsl.Punctuation;
import io.github.serfom256.intentflow.core.dsl.PunctuationToken;
import io.github.serfom256.intentflow.core.dsl.StringToken;
import io.github.serfom256.intentflow.core.dsl.Token;
import io.github.serfom256.intentflow.core.dsl.factories.Positions;
import io.github.serfom256.intentflow.core.pipeline.DslParseException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DefaultLexer implements Lexer {

    private static final Map<String, Keyword> KEYWORDS = Map.ofEntries(
            Map.entry("filter", Keyword.FILTER),
            Map.entry("sort", Keyword.SORT),
            Map.entry("skip", Keyword.SKIP),
            Map.entry("take", Keyword.TAKE),
            Map.entry("and", Keyword.AND),
            Map.entry("or", Keyword.OR),
            Map.entry("not", Keyword.NOT),
            Map.entry("in", Keyword.IN),
            Map.entry("null", Keyword.NULL),
            Map.entry("asc", Keyword.ASC),
            Map.entry("desc", Keyword.DESC),
            Map.entry("true", Keyword.TRUE),
            Map.entry("false", Keyword.FALSE),
            Map.entry("unsupported", Keyword.UNSUPPORTED));

    @Override
    public List<Token> tokenize(DslSource source) {
        String s = source.value();
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int line = 1;
        int column = 1;
        int n = s.length();

        while (i < n) {
            char c = s.charAt(i);

            if (c == '\n') {
                i++;
                line++;
                column = 1;
                continue;
            }
            if (Character.isWhitespace(c)) {
                i++;
                column++;
                continue;
            }

            int startOffset = i;
            int startLine = line;
            int startColumn = column;
            Position pos = Positions.of(startOffset, startLine, startColumn);

            if (isIdentStart(c)) {
                while (i < n && isIdentPart(s.charAt(i))) {
                    i++;
                    column++;
                }
                String text = s.substring(startOffset, i);
                Keyword kw = KEYWORDS.get(text.toLowerCase(Locale.ROOT));
                tokens.add(kw != null
                        ? new KeywordToken(kw, pos)
                        : new IdentifierToken(text, pos));
                continue;
            }

            boolean negative = c == '-' && i + 1 < n && Character.isDigit(s.charAt(i + 1))
                    && !endsValue(tokens);
            if (Character.isDigit(c) || negative) {
                if (negative) {
                    i++;
                    column++;
                }
                while (i < n && Character.isDigit(s.charAt(i))) {
                    i++;
                    column++;
                }
                if (i < n && s.charAt(i) == '.') {
                    i++;
                    column++;
                    if (i >= n || !Character.isDigit(s.charAt(i))) {
                        throw new DslParseException(
                                "expected digit after '.' at line " + line + ", column " + column);
                    }
                    while (i < n && Character.isDigit(s.charAt(i))) {
                        i++;
                        column++;
                    }
                }
                tokens.add(new NumberToken(new BigDecimal(s.substring(startOffset, i)), pos));
                continue;
            }

            if (c == '"') {
                i++;
                column++;
                StringBuilder sb = new StringBuilder();
                while (i < n && s.charAt(i) != '"') {
                    char ch = s.charAt(i);
                    if (ch == '\n') {
                        throw new DslParseException(
                                "unterminated string starting at line " + startLine
                                        + ", column " + startColumn);
                    }
                    if (ch == '\\') {
                        i++;
                        column++;
                        if (i >= n) {
                            throw new DslParseException(
                                    "unterminated escape at line " + line + ", column " + column);
                        }
                        char next = s.charAt(i);
                        char escaped = switch (next) {
                            case '"', '\\' -> next;
                            case 'n' -> '\n';
                            case 't' -> '\t';
                            default -> throw new DslParseException(
                                    "invalid escape '\\" + next + "' at line " + line
                                            + ", column " + column);
                        };
                        sb.append(escaped);
                    } else {
                        sb.append(ch);
                    }
                    i++;
                    column++;
                }
                if (i >= n) {
                    throw new DslParseException(
                            "unterminated string starting at line " + startLine
                                    + ", column " + startColumn);
                }
                i++;
                column++;
                tokens.add(new StringToken(sb.toString(), pos));
                continue;
            }

            if (c == ':' && i + 1 < n && s.charAt(i + 1) == ':') {
                tokens.add(new PunctuationToken(Punctuation.DOUBLE_COLON, pos));
                i += 2;
                column += 2;
                continue;
            }

            if (i + 1 < n) {
                char c2 = s.charAt(i + 1);
                Operator twoChar = switch ("" + c + c2) {
                    case "==" -> Operator.EQ;
                    case "!=" -> Operator.NE;
                    case "<=" -> Operator.LE;
                    case ">=" -> Operator.GE;
                    default -> null;
                };
                if (twoChar != null) {
                    tokens.add(new OperatorToken(twoChar, pos));
                    i += 2;
                    column += 2;
                    continue;
                }
            }

            Operator oneChar = switch (c) {
                case '<' -> Operator.LT;
                case '>' -> Operator.GT;
                case '~' -> Operator.CONTAINS;
                default -> null;
            };
            if (oneChar != null) {
                tokens.add(new OperatorToken(oneChar, pos));
                i++;
                column++;
                continue;
            }

            Punctuation p = switch (c) {
                case '|' -> Punctuation.PIPE;
                case '(' -> Punctuation.LPAREN;
                case ')' -> Punctuation.RPAREN;
                case ',' -> Punctuation.COMMA;
                case '.' -> Punctuation.DOT;
                case '+' -> Punctuation.PLUS;
                case '-' -> Punctuation.MINUS;
                case '*' -> Punctuation.STAR;
                case '/' -> Punctuation.SLASH;
                default -> null;
            };
            if (p != null) {
                tokens.add(new PunctuationToken(p, pos));
                i++;
                column++;
                continue;
            }

            throw new DslParseException(
                    "unexpected character '" + c + "' at line " + line + ", column " + column);
        }

        tokens.add(new EofToken(Positions.of(i, line, column)));
        return List.copyOf(tokens);
    }

    private static boolean endsValue(List<Token> tokens) {
        if (tokens.isEmpty()) {
            return false;
        }
        Token last = tokens.getLast();
        return last instanceof IdentifierToken || last instanceof NumberToken || last instanceof StringToken
                || (last instanceof PunctuationToken p && p.punctuation() == Punctuation.RPAREN)
                || (last instanceof KeywordToken k
                        && (k.keyword() == Keyword.TRUE || k.keyword() == Keyword.FALSE
                                || k.keyword() == Keyword.NULL));
    }

    private static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
