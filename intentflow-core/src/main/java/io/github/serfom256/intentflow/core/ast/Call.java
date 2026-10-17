package io.github.serfom256.intentflow.core.ast;

import java.util.List;

public record Call(ExprFunction function, List<ValueExpr> args) implements ValueExpr {

    public Call {
        args = List.copyOf(args);
    }
}
