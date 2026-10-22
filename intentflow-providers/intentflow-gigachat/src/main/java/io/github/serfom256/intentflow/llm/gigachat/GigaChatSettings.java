package io.github.serfom256.intentflow.llm.gigachat;

import org.jspecify.annotations.Nullable;

import javax.net.ssl.SSLContext;
import java.net.URI;

public record GigaChatSettings(URI baseUrl, URI oauthUrl, String scope, @Nullable SSLContext sslContext) {

    public static final URI BASE_URL = URI.create("https://gigachat.devices.sberbank.ru/api/v1");
    public static final URI OAUTH_URL = URI.create("https://ngw.devices.sberbank.ru:9443/api/v2/oauth");
    public static final String PERSONAL_SCOPE = "GIGACHAT_API_PERS";

    public static GigaChatSettings defaults() {
        return new GigaChatSettings(BASE_URL, OAUTH_URL, PERSONAL_SCOPE, null);
    }
}
