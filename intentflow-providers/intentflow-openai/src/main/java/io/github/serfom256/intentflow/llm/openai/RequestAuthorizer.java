package io.github.serfom256.intentflow.llm.openai;

import java.net.http.HttpRequest;

/**
 * Adds credentials to every request the OpenAI-compatible client sends, for
 * providers whose key is not a fixed
 * header: an Entra ID token for Azure, a short-lived OAuth token for GigaChat.
 * Called once per request, so an
 * implementation can refresh an expiring token; it must be thread-safe.
 */
@FunctionalInterface
public interface RequestAuthorizer {

    RequestAuthorizer NONE = request -> {
    };

    void authorize(HttpRequest.Builder request);

    /**
     * Sends {@code key} in {@code header}; {@code Authorization} gets the
     * {@code Bearer} scheme, any other header
     * the key as is. An empty key sends nothing.
     */
    static RequestAuthorizer apiKey(String key, String header) {
        if (key.isEmpty()) {
            return NONE;
        }
        String value = header.equalsIgnoreCase("Authorization") ? "Bearer " + key : key;
        return request -> request.header(header, value);
    }
}
