package io.github.serfom256.intentflow.llm.gigachat;

import io.github.serfom256.intentflow.core.pipeline.LlmException;
import io.github.serfom256.intentflow.llm.openai.RequestAuthorizer;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GigaChatAuthorizer implements RequestAuthorizer {

    private static final Pattern ACCESS_TOKEN = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern EXPIRES_AT = Pattern.compile("\"expires_at\"\\s*:\\s*(\\d+)");
    private static final Duration REFRESH_BEFORE = Duration.ofMinutes(1);

    private final HttpClient http;
    private final URI oauthUrl;
    private final String authorizationKey;
    private final String scope;
    private final Duration timeout;
    private final Clock clock;
    private @Nullable String token;
    private long expiresAt;

    GigaChatAuthorizer(HttpClient http, URI oauthUrl, String authorizationKey, String scope, Duration timeout,
            Clock clock) {
        this.http = http;
        this.oauthUrl = oauthUrl;
        this.authorizationKey = authorizationKey;
        this.scope = scope;
        this.timeout = timeout;
        this.clock = clock;
    }

    @Override
    public void authorize(HttpRequest.Builder request) {
        request.header("Authorization", "Bearer " + token());
    }

    private synchronized String token() {
        if (token == null || clock.millis() >= expiresAt - REFRESH_BEFORE.toMillis()) {
            refresh();
        }
        return token;
    }

    private void refresh() {
        HttpRequest request = HttpRequest.newBuilder(oauthUrl)
                .timeout(timeout)
                .header("Authorization", "Basic " + authorizationKey)
                .header("RqUID", UUID.randomUUID().toString())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("scope=" + URLEncoder.encode(scope,
                        StandardCharsets.UTF_8)))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new LlmException("GigaChat OAuth at " + oauthUrl + " is not reachable: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("GigaChat OAuth request interrupted", e);
        }
        if (response.statusCode() / 100 != 2) {
            throw new LlmException("GigaChat OAuth returned HTTP " + response.statusCode());
        }
        Matcher accessToken = ACCESS_TOKEN.matcher(response.body());
        Matcher expires = EXPIRES_AT.matcher(response.body());
        if (!accessToken.find() || !expires.find()) {
            throw new LlmException("GigaChat OAuth returned no access token");
        }
        token = accessToken.group(1);
        expiresAt = Long.parseLong(expires.group(1));
    }
}
