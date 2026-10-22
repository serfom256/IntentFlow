package io.github.serfom256.intentflow.core;

import org.jspecify.annotations.Nullable;

import java.util.Map;

public class IntentFlowException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> details;

    public IntentFlowException(ErrorCode code, String message) {
        this(code, message, Map.of(), null);
    }

    public IntentFlowException(ErrorCode code, String message, Map<String, Object> details) {
        this(code, message, details, null);
    }

    public IntentFlowException(ErrorCode code, String message, Map<String, Object> details,
            @Nullable Throwable cause) {
        super(message, cause);
        this.code = code;
        this.details = Map.copyOf(details);
    }

    public ErrorCode code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }
}
