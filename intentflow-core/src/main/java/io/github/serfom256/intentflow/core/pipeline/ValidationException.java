package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ErrorCode;
import io.github.serfom256.intentflow.core.IntentFlowException;

import java.util.Map;

public class ValidationException extends IntentFlowException {

    public ValidationException(String message) {
        super(ErrorCode.INVALID_QUERY, message);
    }

    public ValidationException(ErrorCode code, String message, Map<String, Object> details) {
        super(code, message, details);
    }
}
