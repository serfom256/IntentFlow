package io.github.serfom256.intentflow.llm.gigachat;

import io.github.serfom256.intentflow.core.pipeline.LlmClient;
import io.github.serfom256.intentflow.llm.openai.LlmHttpOptions;
import io.github.serfom256.intentflow.llm.openai.OpenAiCompatibleLlmClient;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.time.Clock;
import java.time.Duration;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GigaChatLlmClients {

    public static LlmClient of(String model, String authorizationKey, GigaChatSettings settings, Duration timeout,
            Clock clock, LlmHttpOptions options) {
        HttpClient.Builder http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10));
        ProxySelector proxy = options.proxy() != null ? options.proxy() : ProxySelector.getDefault();
        if (proxy != null) {
            http.proxy(proxy);
        }
        if (settings.sslContext() != null) {
            http.sslContext(settings.sslContext());
        }
        GigaChatAuthorizer authorizer = new GigaChatAuthorizer(http.build(), settings.oauthUrl(), authorizationKey,
                settings.scope(), timeout, clock);
        return OpenAiCompatibleLlmClient.builder(settings.baseUrl(), model).authorizer(authorizer).temperature(null)
                .sslContext(settings.sslContext()).timeout(timeout).clock(clock).options(options).build();
    }

    public static SSLContext trustStore(Path path, String password, String type) {
        try (InputStream in = Files.newInputStream(path)) {
            KeyStore store = KeyStore.getInstance(type);
            store.load(in, password.toCharArray());
            TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trust.init(store);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trust.getTrustManagers(), null);
            return context;
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Could not load the GigaChat trust store " + path, e);
        }
    }
}
