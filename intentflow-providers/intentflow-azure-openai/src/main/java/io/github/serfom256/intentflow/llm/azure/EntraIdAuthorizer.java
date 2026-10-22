package io.github.serfom256.intentflow.llm.azure;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.DefaultAzureCredentialBuilder;
import io.github.serfom256.intentflow.core.pipeline.LlmException;
import io.github.serfom256.intentflow.llm.openai.RequestAuthorizer;
import org.jspecify.annotations.Nullable;

import java.net.http.HttpRequest;
import java.time.OffsetDateTime;

final class EntraIdAuthorizer implements RequestAuthorizer {

    private static final String SCOPE = "https://cognitiveservices.azure.com/.default";

    private final TokenCredential credential;
    private @Nullable AccessToken token;

    EntraIdAuthorizer() {
        this.credential = new DefaultAzureCredentialBuilder().build();
    }

    @Override
    public void authorize(HttpRequest.Builder request) {
        request.header("Authorization", "Bearer " + token());
    }

    private synchronized String token() {
        if (token == null || token.getExpiresAt().isBefore(OffsetDateTime.now().plusMinutes(5))) {
            try {
                token = credential.getTokenSync(new TokenRequestContext().addScopes(SCOPE));
            } catch (RuntimeException e) {
                throw new LlmException("Could not get an Entra ID token for Azure OpenAI: " + e.getMessage(), e);
            }
        }
        return token.getToken();
    }
}
