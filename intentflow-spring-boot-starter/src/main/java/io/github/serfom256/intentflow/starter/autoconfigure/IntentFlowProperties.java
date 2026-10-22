package io.github.serfom256.intentflow.starter.autoconfigure;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.nio.file.Path;
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
        @DefaultValue Cassandra cassandra,
        @DefaultValue Audit audit,
        @DefaultValue Logging logging,
        @DefaultValue Results results) {

    public ZoneId zone() {
        return timeZone == null || timeZone.isBlank() ? ZoneId.systemDefault() : ZoneId.of(timeZone.strip());
    }

    public record Llm(
            @Nullable String provider,
            @Nullable URI baseUrl,
            @Nullable String model,
            @DefaultValue("") String apiKey,
            @DefaultValue("60s") Duration timeout,
            @DefaultValue("16000") int maxTokens,
            @DefaultValue("1") int maxRepairs,
            @DefaultValue("Authorization") String apiKeyHeader,
            @DefaultValue Map<String, String> headers,
            @Nullable URI proxy,
            @DefaultValue("2") int maxRetries,
            @DefaultValue("500ms") Duration retryBackoff,
            @DefaultValue("30s") Duration healthInterval,
            @DefaultValue Concurrency concurrency,
            @DefaultValue CircuitBreaker circuitBreaker,
            @DefaultValue Fallback fallback,
            @DefaultValue RateLimit rateLimit,
            @DefaultValue Anthropic anthropic,
            @DefaultValue Vertex vertex,
            @DefaultValue Bedrock bedrock,
            @DefaultValue GigaChat gigachat) {

        public record Anthropic(@Nullable String effort, @DefaultValue("true") boolean refusalFallback) {
        }

        public record Vertex(@Nullable String project, @DefaultValue("global") String location) {
        }

        public record Bedrock(@Nullable String region, @Nullable Float temperature,
                @DefaultValue("false") boolean promptCaching) {
        }

        public record GigaChat(@Nullable URI oauthUrl, @DefaultValue("GIGACHAT_API_PERS") String scope,
                @Nullable Path trustStore, @DefaultValue("") String trustStorePassword,
                @DefaultValue("PKCS12") String trustStoreType) {
        }

        public record Concurrency(
                @DefaultValue("0") int maxCalls,
                @DefaultValue("10s") Duration maxWait) {
        }

        public record CircuitBreaker(
                @DefaultValue("5") int failureThreshold,
                @DefaultValue("30s") Duration openFor) {
        }

        public record Fallback(@Nullable URI baseUrl, @Nullable String model, @Nullable String apiKey) {
        }

        public record RateLimit(
                @DefaultValue("0") int questions,
                @DefaultValue("1m") Duration period,
                @DefaultValue("principal") Per per,
                @DefaultValue("memory") Store store,
                @DefaultValue ZooKeeper zookeeper) {

            public enum Per {
                PRINCIPAL, TENANT
            }

            public enum Store {
                MEMORY, ZOOKEEPER
            }

            public record ZooKeeper(@Nullable String connect,
                    @DefaultValue("/intentflow/rate-limit") String path,
                    @DefaultValue("true") boolean failOpen) {
            }
        }
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

    public record Audit(@DefaultValue("false") boolean enabled) {
    }

    public record Logging(@DefaultValue("false") boolean values) {
    }

    public record Results(@DefaultValue("false") boolean visibleFieldsOnly) {
    }
}
