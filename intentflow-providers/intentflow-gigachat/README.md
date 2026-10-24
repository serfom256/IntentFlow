# intentflow-gigachat

Connects IntentFlow to Sber's GigaChat. It exchanges the authorization key for an access token at GigaChat's OAuth
endpoint, renews the token before it expires, and sends chat requests through the `intentflow-openai` client.

```kotlin
implementation("io.github.serfom256:intentflow-gigachat:0.5.0")
```

```yaml
intentflow:
  llm:
    provider: gigachat
    model: <model, e.g. GigaChat-2-Max>
    api-key: ${GIGACHAT_AUTHORIZATION_KEY}
    gigachat:
      scope: GIGACHAT_API_PERS
      trust-store: /etc/ssl/russian-trusted-ca.p12
      trust-store-password: ${TRUST_STORE_PASSWORD}
```

| Setting                                   | Default                                              | Meaning                                         |
| ----------------------------------------- | ---------------------------------------------------- | ----------------------------------------------- |
| intentflow.llm.api-key                    | -                                                    | The authorization key from the GigaChat studio (Base64 of client id and secret) |
| intentflow.llm.base-url                   | https://gigachat.devices.sberbank.ru/api/v1          | Chat API                                        |
| intentflow.llm.gigachat.scope             | GIGACHAT_API_PERS                                    | `GIGACHAT_API_PERS`, `GIGACHAT_API_B2B` or `GIGACHAT_API_CORP` |
| intentflow.llm.gigachat.oauth-url         | https://ngw.devices.sberbank.ru:9443/api/v2/oauth    | Token endpoint                                  |
| intentflow.llm.gigachat.trust-store       | the JVM's                                            | Trust store with the Russian Trusted Root CA    |
| intentflow.llm.gigachat.trust-store-password | empty                                             | Its password                                    |
| intentflow.llm.gigachat.trust-store-type  | PKCS12                                               | `PKCS12` or `JKS`                               |

GigaChat's certificates are issued by the Russian Trusted Root CA, which the JDK does not trust by default: import
it into a trust store and point `trust-store` at it (or add it to the JVM's `cacerts`). Requests use the model's
default temperature.
