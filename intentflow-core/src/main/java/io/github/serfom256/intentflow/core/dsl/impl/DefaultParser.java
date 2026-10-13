package io.github.serfom256.intentflow.core.dsl.impl;

import io.github.serfom256.intentflow.core.ast.Aggregate;
import io.github.serfom256.intentflow.core.ast.AggregateFunction;
import io.github.serfom256.intentflow.core.ast.And;
import io.github.serfom256.intentflow.core.ast.AstQuery;
import io.github.serfom256.intentflow.core.ast.BoolLiteral;
import io.github.serfom256.intentflow.core.ast.Compare;
import io.github.serfom256.intentflow.core.ast.ComparisonOp;
import io.github.serfom256.intentflow.core.ast.DslLiteral;
import io.github.serfom256.intentflow.core.ast.FilterExpr;
import io.github.serfom256.intentflow.core.ast.In;
import io.github.serfom256.intentflow.core.ast.IsNotNull;
import io.github.serfom256.intentflow.core.ast.IsNull;
import io.github.serfom256.intentflow.core.ast.Not;
import io.github.serfom256.intentflow.core.ast.NullLiteral;
import io.github.serfom256.intentflow.core.ast.NumberLiteral;
import io.github.serfom256.intentflow.core.ast.Or;
import io.github.serfom256.intentflow.core.ast.Skip;
import io.github.serfom256.intentflow.core.ast.Take;
import io.github.serfom256.intentflow.core.ast.TextLiteral;
import io.github.serfom256.intentflow.core.ast.factories.AstQueries;
import io.github.serfom256.intentflow.core.ast.factories.Skips;
import io.github.serfom256.intentflow.core.ast.factories.Takes;
import io.github.serfom256.intentflow.core.catalog.LogicalFieldName;
import io.github.serfom256.intentflow.core.catalog.ServiceName;
import io.github.serfom256.intentflow.core.catalog.SortKey;
import io.github.serfom256.intentflow.core.catalog.factories.LogicalFieldNames;
import io.github.serfom256.intentflow.core.catalog.factories.ServiceNames;
import io.github.serfom256.intentflow.core.catalog.factories.SortKeys;
import io.github.serfom256.intentflow.core.dsl.EofToken;
import io.github.serfom256.intentflow.core.dsl.IdentifierToken;
import io.github.serfom256.intentflow.core.dsl.Keyword;
import io.github.serfom256.intentflow.core.dsl.KeywordToken;
import io.github.serfom256.intentflow.core.dsl.NumberToken;
import io.github.serfom256.intentflow.core.dsl.Operator;
import io.github.serfom256.intentflow.core.dsl.OperatorToken;
import io.github.serfom256.intentflow.core.dsl.Parser;
import io.github.serfom256.intentflow.core.dsl.Position;
import io.github.serfom256.intentflow.core.dsl.Punctuation;
import io.github.serfom256.intentflow.core.dsl.PunctuationToken;
import io.github.serfom256.intentflow.core.dsl.StringToken;
import io.github.serfom256.intentflow.core.dsl.Token;
import io.github.serfom256.intentflow.core.pipeline.DslParseException;
import io.github.serfom256.intentflow.core.pipeline.UnsupportedRequestException;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class DefaultParser implements Parser {

    @Override
    public AstQuery parse(List<Token> tokens) {
        return new ParseState(tokens).parseQuery();
    }

    @RequiredArgsConstructor
    private static final class ParseState {

        private final List<Token> tokens;
        private int pos;

        AstQuery parseQuery() {
            if (peekKeyword(Keyword.UNSUPPORTED)) {
                throw parseUnsupported();
            }
            ServiceName service = parseService();

            FilterExpr filter = null;
            List<SortKey> sort = List.of();
            Skip skip = null;
            Take take = null;

            boolean sawFilter = false;
            boolean sawSort = false;
            boolean sawSkip = false;
            boolean sawTake = false;
            List<LogicalFieldName> groupBy = null;
            AggregateFunction function = null;
            LogicalFieldName aggregated = null;

            while (peekPunct(Punctuation.PIPE)) {
                advance();
                Token t = peek();
                if (t instanceof IdentifierToken id && id.name().equals("group")) {
                    if (groupBy != null) throw error("duplicate 'group' stage", t);
                    advance();
                    groupBy = parseFieldList();
                    continue;
                }
                if (t instanceof IdentifierToken id && AggregateFunction.fromKeyword(id.name()).isPresent()) {
                    if (function != null) throw error("only one aggregate stage is allowed", t);
                    function = AggregateFunction.fromKeyword(id.name()).orElseThrow();
                    advance();
                    if (function != AggregateFunction.COUNT) {
                        aggregated = parseFieldName("expected field after '" + function.keyword() + "'");
                    }
                    continue;
                }
                if (!(t instanceof KeywordToken kw)) {
                    throw error("expected stage keyword after '|'", t);
                }
                switch (kw.keyword()) {
                    case FILTER -> {
                        if (sawFilter) throw error("duplicate 'filter' stage", t);
                        sawFilter = true;
                        advance();
                        filter = parseOrExpr();
                    }
                    case SORT -> {
                        if (sawSort) throw error("duplicate 'sort' stage", t);
                        sawSort = true;
                        advance();
                        sort = parseSort();
                    }
                    case SKIP -> {
                        if (sawSkip) throw error("duplicate 'skip' stage", t);
                        sawSkip = true;
                        advance();
                        skip = parseSkip();
                    }
                    case TAKE -> {
                        if (sawTake) throw error("duplicate 'take' stage", t);
                        sawTake = true;
                        advance();
                        take = parseTake();
                    }
                    default -> throw error("unknown stage keyword '" + kw.keyword() + "'", t);
                }
            }

            expectEof();
            if (groupBy != null && function == null) {
                throw new DslParseException("'group' needs an aggregate stage: count, sum, avg, min or max");
            }
            Aggregate aggregate = function == null ? null
                    : new Aggregate(function, aggregated, groupBy == null ? List.of() : groupBy);
            return AstQueries.of(service, filter, sort, skip, take, aggregate);
        }

        List<LogicalFieldName> parseFieldList() {
            List<LogicalFieldName> fields = new ArrayList<>();
            fields.add(parseFieldName("expected field to group by"));
            while (peekPunct(Punctuation.COMMA)) {
                advance();
                fields.add(parseFieldName("expected field to group by"));
            }
            return fields;
        }

        LogicalFieldName parseFieldName(String message) {
            Token t = peek();
            if (!(t instanceof IdentifierToken id)) {
                throw error(message, t);
            }
            advance();
            return LogicalFieldNames.of(dotted(id.name()));
        }

        String dotted(String first) {
            if (!peekPunct(Punctuation.DOT)) {
                return first;
            }
            advance();
            Token t = peek();
            if (!(t instanceof IdentifierToken next)) {
                throw error("expected field name after '" + first + ".'", t);
            }
            advance();
            return first + "." + next.name();
        }

        UnsupportedRequestException parseUnsupported() {
            advance();
            String reason = "no reason given";
            if (peek() instanceof StringToken s) {
                advance();
                reason = s.value();
            }
            expectEof();
            return new UnsupportedRequestException(reason);
        }

        ServiceName parseService() {
            Token t = peek();
            if (!(t instanceof IdentifierToken id)) {
                throw error("expected service name", t);
            }
            advance();
            return ServiceNames.of(id.name());
        }

        FilterExpr parseOrExpr() {
            FilterExpr left = parseAndExpr();
            while (peekKeyword(Keyword.OR)) {
                advance();
                FilterExpr right = parseAndExpr();
                left = new Or(left, right);
            }
            return left;
        }

        FilterExpr parseAndExpr() {
            FilterExpr left = parseNotExpr();
            while (peekKeyword(Keyword.AND)) {
                advance();
                FilterExpr right = parseNotExpr();
                left = new And(left, right);
            }
            return left;
        }

        FilterExpr parseNotExpr() {
            if (peekKeyword(Keyword.NOT)) {
                advance();
                return new Not(parseNotExpr());
            }
            return parsePrimary();
        }

        FilterExpr parsePrimary() {
            Token t = peek();
            if (t instanceof PunctuationToken p && p.punctuation() == Punctuation.LPAREN) {
                advance();
                FilterExpr inner = parseOrExpr();
                expectPunct(Punctuation.RPAREN, "expected ')'");
                return inner;
            }
            if (t instanceof IdentifierToken id) {
                advance();
                LogicalFieldName field = LogicalFieldNames.of(dotted(id.name()));

                if (peekKeyword(Keyword.IN)) {
                    advance();
                    return parseInList(field);
                }

                Token next = peek();
                if (!(next instanceof OperatorToken op)) {
                    throw error("expected comparison operator or 'in' after field '"
                            + id.name() + "'", next);
                }
                advance();

                if (peekKeyword(Keyword.NULL)) {
                    advance();
                    if (op.operator() == Operator.EQ) return new IsNull(field);
                    if (op.operator() == Operator.NE) return new IsNotNull(field);
                    throw error("'null' is only valid with '==' or '!='", next);
                }

                DslLiteral value = parseValue();
                return new Compare(field, mapOp(op.operator()), value);
            }
            throw error("expected filter expression", t);
        }

        FilterExpr parseInList(LogicalFieldName field) {
            expectPunct(Punctuation.LPAREN, "expected '(' after 'in'");
            List<DslLiteral> values = new ArrayList<>();
            values.add(parseValue());
            while (peekPunct(Punctuation.COMMA)) {
                advance();
                values.add(parseValue());
            }
            expectPunct(Punctuation.RPAREN, "expected ')'");
            return new In(field, values);
        }

        DslLiteral parseValue() {
            Token t = peek();
            if (t instanceof StringToken s) {
                advance();
                return new TextLiteral(s.value());
            }
            if (t instanceof NumberToken n) {
                advance();
                return new NumberLiteral(n.value());
            }
            if (t instanceof KeywordToken kw) {
                switch (kw.keyword()) {
                    case TRUE  -> { advance(); return new BoolLiteral(true); }
                    case FALSE -> { advance(); return new BoolLiteral(false); }
                    case NULL  -> { advance(); return new NullLiteral(); }
                    default    -> { }
                }
            }
            throw error("expected value literal (string, number, true, false, null)", t);
        }

        List<SortKey> parseSort() {
            List<SortKey> keys = new ArrayList<>();
            keys.add(parseSortItem());
            while (peekPunct(Punctuation.COMMA)) {
                advance();
                keys.add(parseSortItem());
            }
            return keys;
        }

        SortKey parseSortItem() {
            Token t = peek();
            if (!(t instanceof IdentifierToken id)) {
                throw error("expected sort field", t);
            }
            advance();
            LogicalFieldName field = LogicalFieldNames.of(dotted(id.name()));
            SortKey.Direction dir = SortKey.Direction.ASC;
            if (peek() instanceof KeywordToken kw) {
                if (kw.keyword() == Keyword.ASC) {
                    advance();
                } else if (kw.keyword() == Keyword.DESC) {
                    advance();
                    dir = SortKey.Direction.DESC;
                }
            }
            return SortKeys.of(field, dir);
        }

        Skip parseSkip() {
            Token at = peek();
            int n = toInt(expectNumber("expected integer after 'skip'"), "skip");
            if (n < 0) {
                throw error("'skip' must be zero or positive, got " + n, at);
            }
            return Skips.of(n);
        }

        Take parseTake() {
            Token at = peek();
            int n = toInt(expectNumber("expected integer after 'take'"), "take");
            if (n <= 0) {
                throw error("'take' must be positive, got " + n, at);
            }
            return Takes.of(n);
        }

        private static ComparisonOp mapOp(Operator op) {
            return switch (op) {
                case EQ -> ComparisonOp.EQ;
                case NE -> ComparisonOp.NE;
                case LT -> ComparisonOp.LT;
                case LE -> ComparisonOp.LE;
                case GT -> ComparisonOp.GT;
                case GE -> ComparisonOp.GE;
                case CONTAINS -> ComparisonOp.CONTAINS;
            };
        }

        private int toInt(BigDecimal n, String stage) {
            try {
                return n.intValueExact();
            } catch (ArithmeticException ex) {
                throw new DslParseException(
                        "'" + stage + "' requires an integer value, got " + n);
            }
        }

        private Token peek() {
            return tokens.get(pos);
        }

        private boolean peekKeyword(Keyword k) {
            return peek() instanceof KeywordToken kt && kt.keyword() == k;
        }

        private boolean peekPunct(Punctuation p) {
            return peek() instanceof PunctuationToken pt && pt.punctuation() == p;
        }

        private void advance() {
            pos++;
        }

        private void expectPunct(Punctuation p, String message) {
            Token t = peek();
            if (!(t instanceof PunctuationToken pt) || pt.punctuation() != p) {
                throw error(message, t);
            }
            advance();
        }

        private BigDecimal expectNumber(String message) {
            Token t = peek();
            if (!(t instanceof NumberToken n)) {
                throw error(message, t);
            }
            advance();
            return n.value();
        }

        private void expectEof() {
            Token t = peek();
            if (!(t instanceof EofToken)) {
                throw error("unexpected token after pipeline", t);
            }
        }

        private static DslParseException error(String message, Token at) {
            Position p = at.position();
            return new DslParseException(
                    message + " at line " + p.line() + ", column " + p.column());
        }
    }
}
