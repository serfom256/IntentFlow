package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ErrorCode;
import io.github.serfom256.intentflow.core.IntentFlowException;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public class DslParseException extends IntentFlowException {

    private final @Nullable Diagnostic diagnostic;

    public DslParseException(String message) {
        this(message, null);
    }

    public DslParseException(String message, @Nullable Diagnostic diagnostic) {
        super(ErrorCode.PARSE, message);
        this.diagnostic = diagnostic;
    }

    public Optional<Diagnostic> diagnostic() {
        return Optional.ofNullable(diagnostic);
    }
}
