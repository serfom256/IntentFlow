package io.github.serfom256.intentflow.core.pipeline;

public class ScanLimitExceededException extends RuntimeException {

    public ScanLimitExceededException(String query, int limit) {
        super("The query read more than " + limit + " rows without finishing; narrow the question "
                + "or raise the scan limit: " + query);
    }
}
