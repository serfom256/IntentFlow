package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.IntentFlowException;
import lombok.Getter;
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
public class UnsupportedRequestException extends IntentFlowException {

    private final String reason;

    public UnsupportedRequestException(String reason) {
        super("Request cannot be answered: " + reason);
        this.reason = reason;
    }
}
