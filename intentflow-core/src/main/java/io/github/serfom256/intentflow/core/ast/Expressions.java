package io.github.serfom256.intentflow.core.ast;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.stream.Collectors;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Expressions {

    public static String print(ValueExpr expr) {
        return switch (expr) {
            case FieldRef f -> f.field().value();
            case LiteralExpr l -> print(l.value());
            case Call c -> c.function().keyword() + "("
                    + c.args().stream().map(Expressions::print).collect(Collectors.joining(", ")) + ")";
            case Arith a -> "(" + print(a.left()) + " " + a.op().symbol() + " " + print(a.right()) + ")";
            case CastExpr c -> "cast(" + print(c.value()) + " as " + c.type().keyword() + ")";
        };
    }

    public static String print(DslLiteral literal) {
        return switch (literal) {
            case TextLiteral t -> "\"" + t.value().replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\t", "\\t") + "\"";
            case NumberLiteral n -> plain(n.value());
            case BoolLiteral b -> Boolean.toString(b.value());
            case NullLiteral n -> "null";
        };
    }

    private static String plain(BigDecimal value) {
        return value.toPlainString();
    }
}
