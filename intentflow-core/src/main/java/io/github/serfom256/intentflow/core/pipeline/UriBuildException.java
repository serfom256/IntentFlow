package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ErrorCode;
import io.github.serfom256.intentflow.core.IntentFlowException;

public class UriBuildException extends IntentFlowException {

    public UriBuildException(String message) {
        super(ErrorCode.URI_BUILD, message);
    }
}
