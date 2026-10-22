package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ErrorCode;
import io.github.serfom256.intentflow.core.IntentFlowException;

import java.util.Map;

public class UnsupportedRequestException extends IntentFlowException {

    private final String reason;

    public UnsupportedRequestException(String reason) {
        super(ErrorCode.DECLINED, "Request cannot be answered: " + reason, Map.of("reason", reason));
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
