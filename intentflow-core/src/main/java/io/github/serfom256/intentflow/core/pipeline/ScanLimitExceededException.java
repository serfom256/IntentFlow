package io.github.serfom256.intentflow.core.pipeline;

import io.github.serfom256.intentflow.core.ErrorCode;
import io.github.serfom256.intentflow.core.IntentFlowException;

import java.util.Map;

public class ScanLimitExceededException extends IntentFlowException {

    public ScanLimitExceededException(String query, int limit) {
        super(ErrorCode.SCAN_LIMIT, "The query read more than " + limit + " rows without finishing; narrow the "
                + "question or raise the scan limit: " + query, Map.of("limit", limit));
    }
}
