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
import org.jspecify.annotations.Nullable;

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
        return new Scanner(source.value()).run();
    }

    private static final class Scanner {

        private final String s;
        private final int n;
        private final List<Token> tokens = new ArrayList<>();
        private int i;
        private int line = 1;
        private int column = 1;

        Scanner(String s) {
            this.s = s;
            this.n = s.length();
        }

        List<Token> run() {
            while (i < n) {
                char c = s.charAt(i);
                if (c == '\n') {
                    i++;
                    line++;
                    column = 1;
                } else if (Character.isWhitespace(c)) {
                    advance(1);
                } else {
                    token(c);
                }
            }
            tokens.add(new EofToken(Positions.of(i, line, column)));
            return List.copyOf(tokens);
        }

        private void token(char c) {
            Position pos = Positions.of(i, line, column);
            if (isIdentStart(c)) {
                identifier(pos);
            } else if (Character.isDigit(c) || negativeNumber(c)) {
                number(pos);
            } else if (c == '"') {
                string(pos);
            } else if (!symbol(c, pos)) {
                throw new DslParseException(
                        "unexpected character '" + c + "' at line " + line + ", column " + column);
            }
        }

        private void identifier(Position pos) {
            int start = i;
            while (i < n && isIdentPart(s.charAt(i))) {
                advance(1);
            }
            String text = s.substring(start, i);
            Keyword kw = KEYWORDS.get(text.toLowerCase(Locale.ROOT));
            tokens.add(kw != null ? new KeywordToken(kw, pos) : new IdentifierToken(text, pos));
        }

        private boolean negativeNumber(char c) {
            return c == '-' && i + 1 < n && Character.isDigit(s.charAt(i + 1)) && !endsValue(tokens);
        }

        private void number(Position pos) {
            int start = i;
            if (s.charAt(i) == '-') {
                advance(1);
            }
            digits();
            if (i < n && s.charAt(i) == '.') {
                advance(1);
                if (i >= n || !Character.isDigit(s.charAt(i))) {
                    throw new DslParseException("expected digit after '.' at line " + line + ", column " + column);
                }
                digits();
            }
            tokens.add(new NumberToken(new BigDecimal(s.substring(start, i)), pos));
        }

        private void digits() {
            while (i < n && Character.isDigit(s.charAt(i))) {
                advance(1);
            }
        }

        private void string(Position pos) {
            String unterminated = "unterminated string starting at line " + line + ", column " + column;
            advance(1);
            StringBuilder sb = new StringBuilder();
            while (i < n && s.charAt(i) != '"') {
                char ch = s.charAt(i);
                if (ch == '\n') {
                    throw new DslParseException(unterminated);
                }
                sb.append(ch == '\\' ? escape() : ch);
                advance(1);
            }
            if (i >= n) {
                throw new DslParseException(unterminated);
            }
            advance(1);
            tokens.add(new StringToken(sb.toString(), pos));
        }

        private char escape() {
            advance(1);
            if (i >= n) {
                throw new DslParseException("unterminated escape at line " + line + ", column " + column);
            }
            char next = s.charAt(i);
            return switch (next) {
                case '"', '\\' -> next;
                case 'n' -> '\n';
                case 't' -> '\t';
                default -> throw new DslParseException(
                        "invalid escape '\\" + next + "' at line " + line + ", column " + column);
            };
        }

        private boolean symbol(char c, Position pos) {
            if (c == ':' && i + 1 < n && s.charAt(i + 1) == ':') {
                emit(new PunctuationToken(Punctuation.DOUBLE_COLON, pos), 2);
                return true;
            }
            Operator twoChar = i + 1 < n ? twoCharOperator("" + c + s.charAt(i + 1)) : null;
            if (twoChar != null) {
                emit(new OperatorToken(twoChar, pos), 2);
                return true;
            }
            Operator oneChar = oneCharOperator(c);
            if (oneChar != null) {
                emit(new OperatorToken(oneChar, pos), 1);
                return true;
            }
            Punctuation punctuation = punctuation(c);
            if (punctuation != null) {
                emit(new PunctuationToken(punctuation, pos), 1);
                return true;
            }
            return false;
        }

        private void emit(Token token, int length) {
            tokens.add(token);
            advance(length);
        }

        private void advance(int length) {
            i += length;
            column += length;
        }
    }

    private static @Nullable Operator twoCharOperator(String text) {
        return switch (text) {
            case "==" -> Operator.EQ;
            case "!=" -> Operator.NE;
            case "<=" -> Operator.LE;
            case ">=" -> Operator.GE;
            default -> null;
        };
    }

    private static @Nullable Operator oneCharOperator(char c) {
        return switch (c) {
            case '<' -> Operator.LT;
            case '>' -> Operator.GT;
            case '~' -> Operator.CONTAINS;
            default -> null;
        };
    }

    private static @Nullable Punctuation punctuation(char c) {
        return switch (c) {
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
