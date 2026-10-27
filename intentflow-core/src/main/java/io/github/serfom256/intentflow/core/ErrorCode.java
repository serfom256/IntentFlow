package io.github.serfom256.intentflow.core;

public enum ErrorCode {

    DECLINED("intentflow.declined"), PARSE("intentflow.parse"), INVALID_QUERY("intentflow.invalid-query"),
    UNKNOWN_FIELD("intentflow.unknown-field"), UNKNOWN_RELATION("intentflow.unknown-relation"),
    TYPE_MISMATCH("intentflow.type-mismatch"), LIMIT_EXCEEDED("intentflow.limit-exceeded"),
    UNSUPPORTED("intentflow.unsupported"), SERVICE_NOT_FOUND("intentflow.service-not-found"),
    ACCESS_DENIED("intentflow.access-denied"), RATE_LIMITED("intentflow.rate-limited"),
    SCAN_LIMIT("intentflow.scan-limit"), LLM_FAILED("intentflow.llm-failed"),
    LLM_UNAVAILABLE("intentflow.llm-unavailable"), URI_BUILD("intentflow.uri-build"),
    INVALID_CURSOR("intentflow.invalid-cursor");

    private final String value;

    ErrorCode(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
