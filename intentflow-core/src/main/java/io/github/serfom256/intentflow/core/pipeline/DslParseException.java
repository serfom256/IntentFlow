package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ErrorCode;
import io.github.serfom256.intentflow.core.IntentFlowException;

public class DslParseException extends IntentFlowException {

    public DslParseException(String message) {
        super(ErrorCode.PARSE, message);
    }
}
