package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ErrorCode;
import io.github.serfom256.intentflow.core.IntentFlowException;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

public class ValidationException extends IntentFlowException {

    private final @Nullable Diagnostic diagnostic;

    public ValidationException(String message) {
        this(ErrorCode.INVALID_QUERY, message, Map.of());
    }

    public ValidationException(ErrorCode code, String message, Map<String, Object> details) {
        this(code, message, details, null);
    }

    public ValidationException(ErrorCode code, String message, Map<String, Object> details,
            @Nullable Diagnostic diagnostic) {
        super(code, message, details);
        this.diagnostic = diagnostic;
    }

    public Optional<Diagnostic> diagnostic() {
        return Optional.ofNullable(diagnostic);
    }
}
