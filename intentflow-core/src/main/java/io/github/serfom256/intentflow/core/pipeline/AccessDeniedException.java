package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ErrorCode;
import io.github.serfom256.intentflow.core.IntentFlowException;

import java.util.Map;

public class AccessDeniedException extends IntentFlowException {

    public AccessDeniedException(String message) {
        super(ErrorCode.ACCESS_DENIED, message);
    }

    public AccessDeniedException(String message, Map<String, Object> details) {
        super(ErrorCode.ACCESS_DENIED, message, details);
    }
}
