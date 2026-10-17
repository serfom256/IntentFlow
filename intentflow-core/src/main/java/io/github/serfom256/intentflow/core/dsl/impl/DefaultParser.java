package io.github.serfom256.intentflow.core.dsl.impl;

import io.github.serfom256.intentflow.core.ast.Aggregate;
import io.github.serfom256.intentflow.core.ast.AggregateFunction;
import io.github.serfom256.intentflow.core.ast.And;
import io.github.serfom256.intentflow.core.ast.Arith;
import io.github.serfom256.intentflow.core.ast.ArithOp;
import io.github.serfom256.intentflow.core.ast.AstQuery;
import io.github.serfom256.intentflow.core.ast.BoolLiteral;
import io.github.serfom256.intentflow.core.ast.Call;
import io.github.serfom256.intentflow.core.ast.CastExpr;
import io.github.serfom256.intentflow.core.ast.CastType;
import io.github.serfom256.intentflow.core.ast.Compare;
import io.github.serfom256.intentflow.core.ast.ComparisonOp;
import io.github.serfom256.intentflow.core.ast.DslLiteral;
import io.github.serfom256.intentflow.core.ast.ExprCompare;
import io.github.serfom256.intentflow.core.ast.ExprFunction;
import io.github.serfom256.intentflow.core.ast.ExprIn;
import io.github.serfom256.intentflow.core.ast.ExprSortKey;
import io.github.serfom256.intentflow.core.ast.Expressions;
import io.github.serfom256.intentflow.core.ast.FieldRef;
import io.github.serfom256.intentflow.core.ast.FilterExpr;
import io.github.serfom256.intentflow.core.ast.In;
import io.github.serfom256.intentflow.core.ast.IsNotNull;
import io.github.serfom256.intentflow.core.ast.IsNull;
import io.github.serfom256.intentflow.core.ast.LiteralExpr;
import io.github.serfom256.intentflow.core.ast.Not;
import io.github.serfom256.intentflow.core.ast.NullLiteral;
import io.github.serfom256.intentflow.core.ast.NumberLiteral;
import io.github.serfom256.intentflow.core.ast.Or;
import io.github.serfom256.intentflow.core.ast.Skip;
import io.github.serfom256.intentflow.core.ast.Take;
import io.github.serfom256.intentflow.core.ast.TextLiteral;
import io.github.serfom256.intentflow.core.ast.TopN;
import io.github.serfom256.intentflow.core.ast.ValueExpr;
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
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class DefaultParser implements Parser {

    @Override
    public AstQuery parse(List<Token> tokens) {
        return new ParseState(tokens).parseQuery();
    }

    private static final class Stages {

        private final Set<String> seen = new HashSet<>();
        private @Nullable FilterExpr filter;
        private List<SortKey> sort = List.of();
        private @Nullable Skip skip;
        private @Nullable Take take;
        private @Nullable TopN top;
        private @Nullable List<ValueExpr> groupBy;
        private @Nullable AggregateFunction function;
        private @Nullable ValueExpr aggregated;

        AstQuery toQuery(ServiceName service) {
            if (groupBy != null && function == null) {
                throw new DslParseException("'group' needs an aggregate stage: count, sum, avg, min or max");
            }
            Aggregate aggregate = function == null ? null
                    : new Aggregate(function, aggregated, groupBy == null ? List.of() : groupBy);
            return AstQueries.of(service, filter, sort, skip, take, aggregate, top);
        }
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
            Stages stages = new Stages();
            while (peekPunct(Punctuation.PIPE)) {
                advance();
                parseStage(stages);
            }
            expectEof();
            return stages.toQuery(service);
        }

        private void parseStage(Stages stages) {
            Token t = peek();
            if (t instanceof IdentifierToken id) {
                if (id.name().equals("top")) {
                    once(stages, "top", t);
                    stages.top = parseTop();
                    return;
                }
                if (id.name().equals("group")) {
                    once(stages, "group", t);
                    stages.groupBy = parseGroupList();
                    return;
                }
                Optional<AggregateFunction> function = AggregateFunction.fromKeyword(id.name());
                if (function.isPresent()) {
                    parseAggregate(stages, function.get(), t);
                    return;
                }
            }
            if (!(t instanceof KeywordToken kw)) {
                throw error("expected stage keyword after '|'", t);
            }
            switch (kw.keyword()) {
                case FILTER -> {
                    once(stages, "filter", t);
                    stages.filter = parseOrExpr();
                }
                case SORT -> {
                    once(stages, "sort", t);
                    stages.sort = parseSort();
                }
                case SKIP -> {
                    once(stages, "skip", t);
                    stages.skip = parseSkip();
                }
                case TAKE -> {
                    once(stages, "take", t);
                    stages.take = parseTake();
                }
                default -> throw error("unknown stage keyword '" + kw.keyword() + "'", t);
            }
        }

        private void once(Stages stages, String stage, Token at) {
            if (!stages.seen.add(stage)) {
                throw error("duplicate '" + stage + "' stage", at);
            }
            advance();
        }

        private void parseAggregate(Stages stages, AggregateFunction function, Token at) {
            if (stages.function != null) {
                throw error("only one aggregate stage is allowed", at);
            }
            stages.function = function;
            advance();
            if (function != AggregateFunction.COUNT) {
                if (!startsValue(peek())) {
                    throw error("expected field after '" + function.keyword() + "'", peek());
                }
                stages.aggregated = parseValueExpr();
            }
        }

        TopN parseTop() {
            Token at = peek();
            int count = toInt(expectNumber("expected a count after 'top'"), "top");
            if (count <= 0) {
                throw error("'top' must be positive, got " + count, at);
            }
            List<ValueExpr> partition = List.of();
            if (peek() instanceof IdentifierToken per && per.name().equals("per")) {
                advance();
                partition = parseGroupList();
            }
            Token by = peek();
            if (!(by instanceof IdentifierToken byWord) || !byWord.name().equals("by")) {
                throw error("expected 'by' and an order in 'top', e.g. top 3 per category by rating desc", by);
            }
            advance();
            return new TopN(count, partition, parseSort());
        }

        List<ValueExpr> parseGroupList() {
            List<ValueExpr> groups = new ArrayList<>();
            groups.add(parseGroupItem());
            while (peekPunct(Punctuation.COMMA)) {
                advance();
                groups.add(parseGroupItem());
            }
            return groups;
        }

        ValueExpr parseGroupItem() {
            if (!startsValue(peek())) {
                throw error("expected field to group by", peek());
            }
            return parseValueExpr();
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
                int mark = pos;
                try {
                    advance();
                    FilterExpr inner = parseOrExpr();
                    expectPunct(Punctuation.RPAREN, "expected ')'");
                    return inner;
                } catch (DslParseException notAGroup) {
                    pos = mark;
                }
            }
            if (!startsValue(t)) {
                throw error("expected filter expression", t);
            }
            ValueExpr left = parseValueExpr();

            if (peekKeyword(Keyword.IN)) {
                advance();
                List<DslLiteral> values = parseLiteralList();
                return left instanceof FieldRef f ? new In(f.field(), values) : new ExprIn(left, values);
            }

            Token next = peek();
            if (!(next instanceof OperatorToken op)) {
                throw error("expected comparison operator or 'in' after '" + Expressions.print(left) + "'", next);
            }
            advance();

            if (peekKeyword(Keyword.NULL)) {
                advance();
                if (op.operator() != Operator.EQ && op.operator() != Operator.NE) {
                    throw error("'null' is only valid with '==' or '!='", next);
                }
                if (left instanceof FieldRef f) {
                    return op.operator() == Operator.EQ ? new IsNull(f.field()) : new IsNotNull(f.field());
                }
                return new ExprCompare(left, mapOp(op.operator()), new LiteralExpr(new NullLiteral()));
            }

            ValueExpr right = parseValueExpr();
            if (left instanceof FieldRef f && right instanceof LiteralExpr l) {
                return new Compare(f.field(), mapOp(op.operator()), l.value());
            }
            return new ExprCompare(left, mapOp(op.operator()), right);
        }

        boolean startsValue(Token t) {
            return t instanceof IdentifierToken || t instanceof NumberToken || t instanceof StringToken
                    || (t instanceof PunctuationToken p
                            && (p.punctuation() == Punctuation.LPAREN || p.punctuation() == Punctuation.MINUS))
                    || (t instanceof KeywordToken k && (k.keyword() == Keyword.TRUE || k.keyword() == Keyword.FALSE));
        }

        ValueExpr parseValueExpr() {
            ValueExpr left = parseTerm();
            while (peekPunct(Punctuation.PLUS) || peekPunct(Punctuation.MINUS)) {
                ArithOp op = peekPunct(Punctuation.PLUS) ? ArithOp.ADD : ArithOp.SUBTRACT;
                advance();
                left = new Arith(op, left, parseTerm());
            }
            return left;
        }

        ValueExpr parseTerm() {
            ValueExpr left = parseUnary();
            while (peekPunct(Punctuation.STAR) || peekPunct(Punctuation.SLASH)) {
                ArithOp op = peekPunct(Punctuation.STAR) ? ArithOp.MULTIPLY : ArithOp.DIVIDE;
                advance();
                left = new Arith(op, left, parseUnary());
            }
            return left;
        }

        ValueExpr parseUnary() {
            if (peekPunct(Punctuation.MINUS)) {
                advance();
                return new Arith(ArithOp.SUBTRACT, new LiteralExpr(new NumberLiteral(BigDecimal.ZERO)), parseUnary());
            }
            ValueExpr value = parseValuePrimary();
            while (peekPunct(Punctuation.DOUBLE_COLON)) {
                advance();
                value = new CastExpr(value, parseCastType());
            }
            return value;
        }

        ValueExpr parseValuePrimary() {
            Token t = peek();
            if (t instanceof PunctuationToken p && p.punctuation() == Punctuation.LPAREN) {
                advance();
                ValueExpr inner = parseValueExpr();
                expectPunct(Punctuation.RPAREN, "expected ')'");
                return inner;
            }
            if (t instanceof IdentifierToken id) {
                advance();
                if (peekPunct(Punctuation.LPAREN)) {
                    return parseCall(id);
                }
                return new FieldRef(LogicalFieldNames.of(dotted(id.name())));
            }
            return new LiteralExpr(parseValue());
        }

        ValueExpr parseCall(IdentifierToken name) {
            expectPunct(Punctuation.LPAREN, "expected '('");
            if (name.name().equalsIgnoreCase("cast")) {
                ValueExpr value = parseValueExpr();
                Token as = peek();
                if (!(as instanceof IdentifierToken asWord) || !asWord.name().equalsIgnoreCase("as")) {
                    throw error("expected 'as' in cast", as);
                }
                advance();
                CastType type = parseCastType();
                expectPunct(Punctuation.RPAREN, "expected ')'");
                return new CastExpr(value, type);
            }
            ExprFunction function = ExprFunction.fromKeyword(name.name())
                    .orElseThrow(() -> error("unknown function '" + name.name() + "'", name));
            List<ValueExpr> args = new ArrayList<>();
            if (!peekPunct(Punctuation.RPAREN)) {
                args.add(parseValueExpr());
                while (peekPunct(Punctuation.COMMA)) {
                    advance();
                    args.add(parseValueExpr());
                }
            }
            expectPunct(Punctuation.RPAREN, "expected ')'");
            return new Call(function, args);
        }

        CastType parseCastType() {
            Token t = peek();
            if (!(t instanceof IdentifierToken id)) {
                throw error("expected a type: number, integer, text, timestamp or date", t);
            }
            advance();
            return CastType.fromName(id.name())
                    .orElseThrow(() -> error("unknown type '" + id.name()
                            + "'; use number, integer, text, timestamp or date", id));
        }

        List<DslLiteral> parseLiteralList() {
            expectPunct(Punctuation.LPAREN, "expected '(' after 'in'");
            List<DslLiteral> values = new ArrayList<>();
            values.add(parseValue());
            while (peekPunct(Punctuation.COMMA)) {
                advance();
                values.add(parseValue());
            }
            expectPunct(Punctuation.RPAREN, "expected ')'");
            return values;
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
                    case TRUE -> {
                        advance();
                        return new BoolLiteral(true);
                    }
                    case FALSE -> {
                        advance();
                        return new BoolLiteral(false);
                    }
                    case NULL -> {
                        advance();
                        return new NullLiteral();
                    }
                    default -> {
                    }
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
            if (!startsValue(t)) {
                throw error("expected sort field", t);
            }
            ValueExpr value = parseValueExpr();
            SortKey.Direction dir = SortKey.Direction.ASC;
            if (peek() instanceof KeywordToken kw) {
                if (kw.keyword() == Keyword.ASC) {
                    advance();
                } else if (kw.keyword() == Keyword.DESC) {
                    advance();
                    dir = SortKey.Direction.DESC;
                }
            }
            return value instanceof FieldRef f ? SortKeys.of(f.field(), dir) : new ExprSortKey(value, dir);
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
