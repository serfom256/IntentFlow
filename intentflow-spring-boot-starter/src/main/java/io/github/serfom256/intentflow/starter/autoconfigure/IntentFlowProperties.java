package io.github.serfom256.intentflow.starter.autoconfigure;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@ConfigurationProperties("intentflow")
public record IntentFlowProperties(
        @DefaultValue("true") boolean enabled,
        @Nullable String executor,
        @Nullable String dialect,
        @DefaultValue("10000") int maxSkip,
        @DefaultValue("1000") int maxInValues,
        @Nullable String timeZone,
        @DefaultValue Llm llm,
        @DefaultValue Cache cache,
        @DefaultValue Catalog catalog,
        @DefaultValue Schema schema,
        @DefaultValue Sql sql,
        @DefaultValue Cassandra cassandra) {

    public ZoneId zone() {
        return timeZone == null || timeZone.isBlank() ? ZoneId.systemDefault() : ZoneId.of(timeZone.strip());
    }

    public record Llm(
            @DefaultValue("http://localhost:1234/v1") URI baseUrl,
            @Nullable String model,
            @DefaultValue("") String apiKey,
            @DefaultValue("60s") Duration timeout,
            @DefaultValue("1") int maxRepairs,
            @DefaultValue("Authorization") String apiKeyHeader,
            @DefaultValue Map<String, String> headers,
            @Nullable URI proxy,
            @DefaultValue("2") int maxRetries,
            @DefaultValue("500ms") Duration retryBackoff) {
    }

    public record Cache(
            @DefaultValue("10m") Duration ttl,
            @DefaultValue("10000") long maxSize) {
    }

    public record Catalog(@DefaultValue List<String> packages) {
    }

    public record Schema(@DefaultValue("true") boolean introspect) {
    }

    public record Sql(
            @DefaultValue List<String> disabledOptimizations,
            @DefaultValue("30s") Duration statementTimeout,
            @DefaultValue("true") boolean readOnly) {
    }

    public record Cassandra(@DefaultValue("10000") int maxScannedRows) {
    }
}
