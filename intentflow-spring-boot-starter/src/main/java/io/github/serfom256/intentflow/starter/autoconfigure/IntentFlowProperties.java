package io.github.serfom256.intentflow.starter.autoconfigure;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;
import java.util.List;

@ConfigurationProperties("intentflow")
public record IntentFlowProperties(
        @DefaultValue("true") boolean enabled,
        @Nullable String executor,
        @Nullable String dialect,
        @DefaultValue Llm llm,
        @DefaultValue Cache cache,
        @DefaultValue Catalog catalog,
        @DefaultValue Cassandra cassandra) {

    public record Llm(
            @DefaultValue("http://localhost:1234/v1") URI baseUrl,
            @Nullable String model,
            @DefaultValue("") String apiKey,
            @DefaultValue("60s") Duration timeout) {
    }

    public record Cache(
            @DefaultValue("10m") Duration ttl,
            @DefaultValue("10000") long maxSize) {
    }

    public record Catalog(@DefaultValue List<String> packages) {
    }

    public record Cassandra(@DefaultValue("10000") int maxScannedRows) {
    }
}
