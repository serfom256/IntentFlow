package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ErrorCode;
import io.github.serfom256.intentflow.core.IntentFlowException;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public class LlmException extends IntentFlowException {

    public LlmException(String message) {
        this(message, null);
    }

    public LlmException(String message, @Nullable Throwable cause) {
        this(ErrorCode.LLM_FAILED, message, Map.of(), cause);
    }

    protected LlmException(ErrorCode code, String message, Map<String, Object> details, @Nullable Throwable cause) {
        super(code, message, details, cause);
    }
}
